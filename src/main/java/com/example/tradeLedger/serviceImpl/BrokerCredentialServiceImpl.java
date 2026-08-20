package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.BrokerCredentialRequest;
import com.example.tradeLedger.dto.BrokerCredentialResponse;
import com.example.tradeLedger.entity.Broker;
import com.example.tradeLedger.entity.BrokerCredential;
import com.example.tradeLedger.entity.TradingAccount;
import com.example.tradeLedger.entity.User;
import com.example.tradeLedger.entity.UserBroker;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.BrokerCredentialRepository;
import com.example.tradeLedger.repository.TradingAccountRepository;
import com.example.tradeLedger.repository.UserBrokerRepository;
import com.example.tradeLedger.service.BrokerCredentialService;
import com.example.tradeLedger.service.BrokerCredentials;
import com.example.tradeLedger.service.CurrentUserService;
import com.example.tradeLedger.utils.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reads and writes {@code broker_credentials} at both levels, encrypting on the
 * way in and decrypting only for {@link #resolve}.
 *
 * The one rule worth stating plainly: <b>resolution is per field</b>. An account
 * override holding only an access token still uses the setup's API key. Every
 * read here goes through {@link #pick}, so there is exactly one place that
 * decides which level a value comes from.
 *
 * Nothing here logs a credential value. The log lines name which fields changed,
 * which is what an audit trail needs, and never what they changed to.
 */
@Service
public class BrokerCredentialServiceImpl implements BrokerCredentialService {

    private static final Logger log = LoggerFactory.getLogger(BrokerCredentialServiceImpl.class);

    private static final int CLIENT_ID_MAX = 100;
    private static final int HINT_CHARS = 4;

    private final CurrentUserService currentUserService;
    private final UserBrokerRepository userBrokerRepository;
    private final TradingAccountRepository tradingAccountRepository;
    private final BrokerCredentialRepository credentialRepository;
    private final SecretCipher cipher;

    public BrokerCredentialServiceImpl(CurrentUserService currentUserService,
                                       UserBrokerRepository userBrokerRepository,
                                       TradingAccountRepository tradingAccountRepository,
                                       BrokerCredentialRepository credentialRepository,
                                       SecretCipher cipher) {
        this.currentUserService = currentUserService;
        this.userBrokerRepository = userBrokerRepository;
        this.tradingAccountRepository = tradingAccountRepository;
        this.credentialRepository = credentialRepository;
        this.cipher = cipher;
    }

    // ------------------------------------------------------------ setup level

    @Override
    @Transactional(readOnly = true)
    public BrokerCredentialResponse getForSetup(String email, UUID userBrokerId) {
        UserBroker setup = requireOwnedSetup(email, userBrokerId);
        BrokerCredential row = setupRow(userBrokerId);
        if (row == null) {
            throw ResourceNotFoundException.of("Credentials for broker setup", userBrokerId);
        }
        return toResponse(setup, null, row, null);
    }

    @Override
    @Transactional
    public BrokerCredentialResponse upsertForSetup(String email, UUID userBrokerId,
                                                   BrokerCredentialRequest request) {
        UserBroker setup = requireOwnedSetup(email, userBrokerId);
        requireBody(request);

        BrokerCredential row = setupRow(userBrokerId);
        boolean fresh = row == null;
        if (fresh) {
            row = new BrokerCredential();
            row.setUserBroker(setup);
        }

        Changes changes = apply(row, request);
        if (changes.none()) {
            throw new StrategyValidationException(
                    "No credential fields supplied. Send at least one field, or an empty string to clear one.");
        }
        // Deleting here instead would be undone by the rollback this throw causes,
        // so the caller is pointed at the endpoint that actually removes the row.
        if (!row.hasAnyValue()) {
            throw new StrategyValidationException(
                    "That would leave the setup with no credentials at all. "
                            + "Use DELETE /api/v1/my-brokers/" + userBrokerId + "/credentials instead.");
        }
        if (changes.secretChanged && !fresh) {
            row.setRotatedAt(OffsetDateTime.now());
        }

        credentialRepository.save(row);
        log.info("UPSERT credentials for broker setup={} broker={} fields=[{}] | user={}",
                userBrokerId, setup.getBroker().getCode(), changes.fields, email);

        return toResponse(setup, null, row, null);
    }

    @Override
    @Transactional
    public void deleteForSetup(String email, UUID userBrokerId) {
        requireOwnedSetup(email, userBrokerId);
        BrokerCredential row = setupRow(userBrokerId);
        if (row != null) {
            credentialRepository.delete(row);
        }
        long overrides = credentialRepository.countByUserBroker_IdAndTradingAccountIsNotNull(userBrokerId);
        if (overrides > 0) {
            // Not an error: those accounts hold their own keys and keep working.
            log.info("DELETE setup credentials={} | {} account override(s) remain | user={}",
                    userBrokerId, overrides, email);
        } else {
            log.info("DELETE setup credentials={} | user={}", userBrokerId, email);
        }
    }

    // ---------------------------------------------------------- account level

    @Override
    @Transactional(readOnly = true)
    public BrokerCredentialResponse getForAccount(String email, UUID tradingAccountId) {
        TradingAccount account = requireOwnedAccount(email, tradingAccountId);
        UserBroker setup = account.getUserBroker();
        BrokerCredential inherited = setupRow(setup.getId());
        BrokerCredential own = accountRow(tradingAccountId);

        if (inherited == null && own == null) {
            throw ResourceNotFoundException.of("Credentials for trading account", tradingAccountId);
        }
        return toResponse(setup, account, inherited, own);
    }

    @Override
    @Transactional
    public BrokerCredentialResponse upsertForAccount(String email, UUID tradingAccountId,
                                                     BrokerCredentialRequest request) {
        TradingAccount account = requireOwnedAccount(email, tradingAccountId);
        requireBody(request);
        UserBroker setup = account.getUserBroker();

        BrokerCredential own = accountRow(tradingAccountId);
        boolean fresh = own == null;
        if (fresh) {
            own = new BrokerCredential();
            own.setUserBroker(setup);
            own.setTradingAccount(account);
        }

        Changes changes = apply(own, request);
        if (changes.none()) {
            throw new StrategyValidationException(
                    "No credential fields supplied. Send at least one field, or an empty string to clear one.");
        }

        // An override with nothing left in it is not an empty override - it is no
        // override. Deleting the row is what puts the account back on the setup's
        // credentials, rather than leaving a row of nulls that shadows nothing.
        if (!own.hasAnyValue()) {
            if (!fresh) {
                credentialRepository.delete(own);
            }
            log.info("CLEARED credential override for trading account={} | user={}", tradingAccountId, email);
            return toResponse(setup, account, setupRow(setup.getId()), null);
        }

        if (changes.secretChanged && !fresh) {
            own.setRotatedAt(OffsetDateTime.now());
        }
        credentialRepository.save(own);
        log.info("UPSERT credential override for trading account={} broker={} fields=[{}] | user={}",
                tradingAccountId, setup.getBroker().getCode(), changes.fields, email);

        return toResponse(setup, account, setupRow(setup.getId()), own);
    }

    @Override
    @Transactional
    public void deleteForAccount(String email, UUID tradingAccountId) {
        requireOwnedAccount(email, tradingAccountId);
        BrokerCredential own = accountRow(tradingAccountId);
        if (own != null) {
            credentialRepository.delete(own);
        }
        log.info("DELETE credential override for trading account={} | user={}", tradingAccountId, email);
    }

    // --------------------------------------------------------------- internal

    @Override
    @Transactional(readOnly = true)
    public BrokerCredentials resolve(UUID tradingAccountId) {
        TradingAccount account = tradingAccountRepository.findById(tradingAccountId)
                .orElseThrow(() -> ResourceNotFoundException.of("Trading account", tradingAccountId));
        UserBroker setup = account.getUserBroker();
        Broker broker = setup != null ? setup.getBroker() : null;

        BrokerCredential inherited = setup != null ? setupRow(setup.getId()) : null;
        BrokerCredential own = accountRow(tradingAccountId);
        if (inherited == null && own == null) {
            throw ResourceNotFoundException.of("Credentials for trading account", tradingAccountId);
        }

        return new BrokerCredentials(
                setup != null ? setup.getId() : null,
                tradingAccountId,
                broker != null ? broker.getCode() : null,
                broker != null ? broker.getAuthType() : null,
                broker != null ? broker.getApiBaseUrl() : null,
                account.getBrokerAccountId(),
                cipher.decrypt(pick(own, inherited, BrokerCredential::getApiKey)),
                cipher.decrypt(pick(own, inherited, BrokerCredential::getApiSecret)),
                cipher.decrypt(pick(own, inherited, BrokerCredential::getAccessToken)),
                cipher.decrypt(pick(own, inherited, BrokerCredential::getRefreshToken)),
                cipher.decrypt(pick(own, inherited, BrokerCredential::getTotpSecret)),
                pick(own, inherited, BrokerCredential::getRedirectUrl),
                pick(own, inherited, BrokerCredential::getClientId),
                pickExpiry(own, inherited),
                pick(own, inherited, BrokerCredential::getVaultRef));
    }

    // -------------------------------------------------------------- ownership

    private UserBroker requireOwnedSetup(String email, UUID userBrokerId) {
        User user = currentUserService.require(email);
        return userBrokerRepository.findByIdAndUser_Id(userBrokerId, user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Broker setup", userBrokerId));
    }

    private TradingAccount requireOwnedAccount(String email, UUID tradingAccountId) {
        User user = currentUserService.require(email);
        return tradingAccountRepository.findByIdAndUser_Id(tradingAccountId, user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Trading account", tradingAccountId));
    }

    private static void requireBody(BrokerCredentialRequest request) {
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
    }

    private BrokerCredential setupRow(UUID userBrokerId) {
        return credentialRepository.findByUserBroker_IdAndTradingAccountIsNull(userBrokerId).orElse(null);
    }

    private BrokerCredential accountRow(UUID tradingAccountId) {
        return credentialRepository.findByTradingAccount_Id(tradingAccountId).orElse(null);
    }

    // ------------------------------------------------------------ field merge

    /**
     * The single place that decides which level a value comes from: the account's
     * own if it has one, the setup's otherwise. Per field, never per row.
     */
    private static String pick(BrokerCredential own, BrokerCredential inherited,
                               java.util.function.Function<BrokerCredential, String> field) {
        String mine = own != null ? field.apply(own) : null;
        if (mine != null) {
            return mine;
        }
        return inherited != null ? field.apply(inherited) : null;
    }

    private static OffsetDateTime pickExpiry(BrokerCredential own, BrokerCredential inherited) {
        OffsetDateTime mine = own != null ? own.getTokenExpiresAt() : null;
        if (mine != null) {
            return mine;
        }
        return inherited != null ? inherited.getTokenExpiresAt() : null;
    }

    // ---------------------------------------------------------------- writing

    /** What one upsert touched, for the audit log and the rotation stamp. */
    private static final class Changes {
        final StringBuilder fields = new StringBuilder();
        boolean secretChanged;

        boolean none() {
            return fields.length() == 0;
        }

        void add(String field) {
            if (fields.length() > 0) {
                fields.append(", ");
            }
            fields.append(field);
        }
    }

    private Changes apply(BrokerCredential row, BrokerCredentialRequest request) {
        Changes changes = new Changes();

        applySecret(changes, "apiKey", request.getApiKey(), row::getApiKey, row::setApiKey);
        applySecret(changes, "apiSecret", request.getApiSecret(), row::getApiSecret, row::setApiSecret);
        applySecret(changes, "accessToken", request.getAccessToken(), row::getAccessToken, row::setAccessToken);
        applySecret(changes, "refreshToken", request.getRefreshToken(), row::getRefreshToken, row::setRefreshToken);
        applySecret(changes, "totpSecret", request.getTotpSecret(), row::getTotpSecret, row::setTotpSecret);

        applyPlain(changes, "redirectUrl", request.getRedirectUrl(), row::setRedirectUrl, 0);
        applyPlain(changes, "clientId", request.getClientId(), row::setClientId, CLIENT_ID_MAX);
        applyPlain(changes, "vaultRef", request.getVaultRef(), row::setVaultRef, 0);

        if (request.getTokenExpiresAt() != null) {
            row.setTokenExpiresAt(request.getTokenExpiresAt());
            changes.add("tokenExpiresAt");
        }
        return changes;
    }

    /**
     * Null leaves the stored value alone, an empty string clears it, anything
     * else replaces it. That asymmetry is what lets a caller rotate one token
     * without resending an API secret it is not allowed to read back.
     */
    private void applySecret(Changes changes, String field, String submitted,
                             Supplier<String> current, Consumer<String> setter) {
        if (submitted == null) {
            return;
        }
        if (submitted.isEmpty()) {
            if (current.get() == null) {
                return;
            }
            setter.accept(null);
            changes.add(field + ":cleared");
            changes.secretChanged = true;
            return;
        }
        setter.accept(cipher.encrypt(submitted.trim()));
        changes.add(field);
        changes.secretChanged = true;
    }

    private void applyPlain(Changes changes, String field, String submitted,
                            Consumer<String> setter, int maxLength) {
        if (submitted == null) {
            return;
        }
        if (submitted.isEmpty()) {
            setter.accept(null);
            changes.add(field + ":cleared");
            return;
        }
        String trimmed = submitted.trim();
        if (maxLength > 0 && trimmed.length() > maxLength) {
            throw new StrategyValidationException(field + " must be at most " + maxLength + " characters");
        }
        setter.accept(trimmed);
        changes.add(field);
    }

    // ---------------------------------------------------------------- reading

    /**
     * @param account null for a setup-level read
     * @param own     the account's override, or null when it inherits everything
     */
    private BrokerCredentialResponse toResponse(UserBroker setup, TradingAccount account,
                                                BrokerCredential inherited, BrokerCredential own) {
        Broker broker = setup.getBroker();
        BrokerCredential newest = own != null ? own : inherited;

        String apiKey = pick(own, inherited, BrokerCredential::getApiKey);
        OffsetDateTime expiry = pickExpiry(own, inherited);

        return new BrokerCredentialResponse(
                setup.getId(),
                account != null ? account.getId() : null,
                broker != null ? broker.getCode() : null,
                broker != null ? broker.getAuthType() : null,
                hint(apiKey),
                apiKey != null,
                pick(own, inherited, BrokerCredential::getApiSecret) != null,
                pick(own, inherited, BrokerCredential::getAccessToken) != null,
                pick(own, inherited, BrokerCredential::getRefreshToken) != null,
                pick(own, inherited, BrokerCredential::getTotpSecret) != null,
                pick(own, inherited, BrokerCredential::getRedirectUrl),
                pick(own, inherited, BrokerCredential::getClientId),
                expiry,
                expiry != null && expiry.isBefore(OffsetDateTime.now()),
                overriddenFields(own),
                pick(own, inherited, BrokerCredential::getVaultRef),
                newest != null ? newest.getRotatedAt() : null,
                newest != null ? newest.getCreatedAt() : null,
                newest != null ? newest.getUpdatedAt() : null);
    }

    /** Which fields this account supplies itself; empty when it inherits all of them. */
    private static List<String> overriddenFields(BrokerCredential own) {
        List<String> fields = new ArrayList<>();
        if (own == null) {
            return fields;
        }
        if (own.getApiKey() != null) fields.add("apiKey");
        if (own.getApiSecret() != null) fields.add("apiSecret");
        if (own.getAccessToken() != null) fields.add("accessToken");
        if (own.getRefreshToken() != null) fields.add("refreshToken");
        if (own.getTotpSecret() != null) fields.add("totpSecret");
        if (own.getRedirectUrl() != null) fields.add("redirectUrl");
        if (own.getClientId() != null) fields.add("clientId");
        if (own.getTokenExpiresAt() != null) fields.add("tokenExpiresAt");
        if (own.getVaultRef() != null) fields.add("vaultRef");
        return fields;
    }

    /**
     * Last four characters of the API key: enough to tell two keys apart, not
     * enough to use. Returns null rather than failing when no key is configured,
     * so the form still renders and only writes report the missing key.
     */
    private String hint(String storedApiKey) {
        if (storedApiKey == null || !cipher.isConfigured()) {
            return null;
        }
        try {
            String plain = cipher.decrypt(storedApiKey);
            if (plain == null || plain.length() <= HINT_CHARS) {
                return "****";
            }
            return "****" + plain.substring(plain.length() - HINT_CHARS);
        } catch (RuntimeException e) {
            log.warn("Could not build API key hint for a credential row: {}", e.getMessage());
            return null;
        }
    }
}

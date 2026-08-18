package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.TradingAccountRequest;
import com.example.tradeLedger.dto.TradingAccountResponse;
import com.example.tradeLedger.entity.AccountCredential;
import com.example.tradeLedger.entity.Exchange;
import com.example.tradeLedger.entity.TradingAccount;
import com.example.tradeLedger.entity.User;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.AccountCredentialRepository;
import com.example.tradeLedger.repository.ExchangeRepository;
import com.example.tradeLedger.repository.StrategySubscriptionRepository;
import com.example.tradeLedger.repository.TradingAccountRepository;
import com.example.tradeLedger.service.CurrentUserService;
import com.example.tradeLedger.service.TradingAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TradingAccountServiceImpl implements TradingAccountService {

    private static final Logger log = LoggerFactory.getLogger(TradingAccountServiceImpl.class);

    private static final int ACCOUNT_NAME_MAX = 100;

    private final CurrentUserService currentUserService;
    private final TradingAccountRepository tradingAccountRepository;
    private final AccountCredentialRepository credentialRepository;
    private final ExchangeRepository exchangeRepository;
    private final StrategySubscriptionRepository subscriptionRepository;

    public TradingAccountServiceImpl(CurrentUserService currentUserService,
                                     TradingAccountRepository tradingAccountRepository,
                                     AccountCredentialRepository credentialRepository,
                                     ExchangeRepository exchangeRepository,
                                     StrategySubscriptionRepository subscriptionRepository) {
        this.currentUserService = currentUserService;
        this.tradingAccountRepository = tradingAccountRepository;
        this.credentialRepository = credentialRepository;
        this.exchangeRepository = exchangeRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TradingAccountResponse> list(String email) {
        User user = currentUserService.require(email);
        return tradingAccountRepository.findByUser_IdOrderByAccountNameAsc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TradingAccountResponse get(String email, UUID id) {
        User user = currentUserService.require(email);
        return toResponse(requireOwned(user, id));
    }

    @Override
    @Transactional
    public TradingAccountResponse create(String email, TradingAccountRequest request) {
        User user = currentUserService.require(email);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        String accountName = requireAccountName(request.getAccountName());
        Exchange exchange = resolveExchange(request);

        if (tradingAccountRepository.existsByUser_IdAndExchange_IdAndAccountName(
                user.getId(), exchange.getId(), accountName)) {
            throw new ResourceConflictException(
                    "Account '" + accountName + "' already exists on exchange " + exchange.getCode());
        }

        TradingAccount account = new TradingAccount();
        account.setUser(user);
        account.setExchange(exchange);
        account.setAccountName(accountName);
        account.setActive(request.getActive() == null || request.getActive());
        account = tradingAccountRepository.save(account);

        if (request.getVaultRef() != null && !request.getVaultRef().isBlank()) {
            saveCredential(account, request.getVaultRef().trim());
        }

        log.info("CREATE trading account '{}' id={} exchange={} | user={}",
                accountName, account.getId(), exchange.getCode(), email);
        return toResponse(account);
    }

    @Override
    @Transactional
    public TradingAccountResponse update(String email, UUID id, TradingAccountRequest request) {
        User user = currentUserService.require(email);
        TradingAccount account = requireOwned(user, id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        if (request.getAccountName() != null) {
            String accountName = requireAccountName(request.getAccountName());
            if (!accountName.equals(account.getAccountName())
                    && tradingAccountRepository.existsByUser_IdAndExchange_IdAndAccountName(
                            user.getId(), account.getExchange().getId(), accountName)) {
                throw new ResourceConflictException("Account '" + accountName + "' already exists on exchange "
                        + account.getExchange().getCode());
            }
            account.setAccountName(accountName);
        }
        if (request.getActive() != null) {
            if (!request.getActive()) {
                long live = subscriptionRepository.countByTradingAccount_IdAndActiveTrue(id);
                if (live > 0) {
                    log.warn("Deactivating trading account {} with {} active subscription(s) | user={}",
                            id, live, email);
                }
            }
            account.setActive(request.getActive());
        }
        tradingAccountRepository.save(account);

        if (request.getVaultRef() != null && !request.getVaultRef().isBlank()) {
            saveCredential(account, request.getVaultRef().trim());
        }

        log.info("UPDATE trading account {} active={} | user={}", id, account.isActive(), email);
        return toResponse(account);
    }

    @Override
    @Transactional
    public void delete(String email, UUID id) {
        User user = currentUserService.require(email);
        TradingAccount account = requireOwned(user, id);

        long live = subscriptionRepository.countByTradingAccount_IdAndActiveTrue(id);
        if (live > 0) {
            throw new ResourceConflictException("Trading account '" + account.getAccountName() + "' still has "
                    + live + " active subscription(s). Remove them first, or deactivate the account instead.");
        }
        credentialRepository.findByTradingAccount_Id(id).ifPresent(credentialRepository::delete);
        tradingAccountRepository.delete(account);
        log.info("DELETE trading account {} | user={}", id, email);
    }

    // -------------------------------------------------------------- helpers

    private TradingAccount requireOwned(User user, UUID id) {
        return tradingAccountRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Trading account", id));
    }

    private String requireAccountName(String accountName) {
        if (accountName == null || accountName.isBlank()) {
            throw new StrategyValidationException("accountName is required");
        }
        String trimmed = accountName.trim();
        if (trimmed.length() > ACCOUNT_NAME_MAX) {
            throw new StrategyValidationException(
                    "accountName must be at most " + ACCOUNT_NAME_MAX + " characters");
        }
        return trimmed;
    }

    private Exchange resolveExchange(TradingAccountRequest request) {
        if (request.getExchangeId() != null) {
            return exchangeRepository.findById(request.getExchangeId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Exchange", request.getExchangeId()));
        }
        if (request.getExchangeCode() != null && !request.getExchangeCode().isBlank()) {
            String code = request.getExchangeCode().trim().toUpperCase(Locale.ROOT);
            return exchangeRepository.findByCode(code)
                    .orElseThrow(() -> ResourceNotFoundException.of("Exchange", code));
        }
        throw new StrategyValidationException("exchangeId or exchangeCode is required");
    }

    /**
     * Only the Vault pointer is persisted. {@code rotated_at} is stamped whenever
     * the reference changes, which is the audit trail the schema asks for.
     */
    private void saveCredential(TradingAccount account, String vaultRef) {
        AccountCredential credential = credentialRepository.findByTradingAccount_Id(account.getId())
                .orElseGet(() -> {
                    AccountCredential fresh = new AccountCredential();
                    fresh.setTradingAccount(account);
                    return fresh;
                });
        if (credential.getId() != null && !vaultRef.equals(credential.getVaultRef())) {
            credential.setRotatedAt(OffsetDateTime.now());
        }
        credential.setVaultRef(vaultRef);
        credentialRepository.save(credential);
    }

    private TradingAccountResponse toResponse(TradingAccount account) {
        AccountCredential credential = credentialRepository.findByTradingAccount_Id(account.getId()).orElse(null);
        Exchange exchange = account.getExchange();
        return new TradingAccountResponse(
                account.getId(),
                exchange.getId(),
                exchange.getCode(),
                exchange.getName(),
                account.getAccountName(),
                account.isActive(),
                credential != null ? credential.getVaultRef() : null,
                credential != null ? credential.getRotatedAt() : null,
                subscriptionRepository.countByTradingAccount_IdAndActiveTrue(account.getId()),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}

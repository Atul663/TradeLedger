package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.TradingAccountRequest;
import com.example.tradeLedger.dto.TradingAccountResponse;
import com.example.tradeLedger.entity.Broker;
import com.example.tradeLedger.entity.BrokerCredential;
import com.example.tradeLedger.entity.TradingAccount;
import com.example.tradeLedger.entity.User;
import com.example.tradeLedger.entity.UserBroker;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.BrokerCredentialRepository;
import com.example.tradeLedger.repository.StrategySubscriptionRepository;
import com.example.tradeLedger.repository.TradingAccountRepository;
import com.example.tradeLedger.repository.UserBrokerRepository;
import com.example.tradeLedger.service.CurrentUserService;
import com.example.tradeLedger.service.TradingAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TradingAccountServiceImpl implements TradingAccountService {

    private static final Logger log = LoggerFactory.getLogger(TradingAccountServiceImpl.class);

    private static final int ACCOUNT_NAME_MAX = 100;
    private static final int BROKER_ACCOUNT_ID_MAX = 100;

    private final CurrentUserService currentUserService;
    private final TradingAccountRepository tradingAccountRepository;
    private final UserBrokerRepository userBrokerRepository;
    private final BrokerCredentialRepository credentialRepository;
    private final StrategySubscriptionRepository subscriptionRepository;

    public TradingAccountServiceImpl(CurrentUserService currentUserService,
                                     TradingAccountRepository tradingAccountRepository,
                                     UserBrokerRepository userBrokerRepository,
                                     BrokerCredentialRepository credentialRepository,
                                     StrategySubscriptionRepository subscriptionRepository) {
        this.currentUserService = currentUserService;
        this.tradingAccountRepository = tradingAccountRepository;
        this.userBrokerRepository = userBrokerRepository;
        this.credentialRepository = credentialRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TradingAccountResponse> list(String email, UUID userBrokerId) {
        User user = currentUserService.require(email);
        List<TradingAccount> accounts = userBrokerId != null
                ? tradingAccountRepository.findByUserBroker_IdOrderByAccountNameAsc(userBrokerId)
                : tradingAccountRepository.findByUser_IdOrderByAccountNameAsc(user.getId());
        return accounts.stream()
                // The by-setup query is not ownership-scoped on its own, so the
                // filter is applied here rather than trusting the path parameter.
                .filter(a -> a.getUser().getId().equals(user.getId()))
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
        if (request.getUserBrokerId() == null) {
            throw new StrategyValidationException(
                    "userBrokerId is required. Set the broker up first with POST /api/v1/my-brokers.");
        }
        UserBroker setup = userBrokerRepository.findByIdAndUser_Id(request.getUserBrokerId(), user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Broker setup", request.getUserBrokerId()));
        if (!setup.isActive()) {
            throw new StrategyValidationException("Broker setup is not active: " + setup.getLabel());
        }

        String accountName = requireAccountName(request.getAccountName());
        if (tradingAccountRepository.existsByUserBroker_IdAndAccountName(setup.getId(), accountName)) {
            throw new ResourceConflictException(
                    "Account '" + accountName + "' already exists under '" + setup.getLabel() + "'");
        }

        TradingAccount account = new TradingAccount();
        account.setUser(user);
        account.setUserBroker(setup);
        account.setAccountName(accountName);
        account.setBrokerAccountId(trimTo(request.getBrokerAccountId(), BROKER_ACCOUNT_ID_MAX, "brokerAccountId"));
        account.setActive(request.getActive() == null || request.getActive());
        account = tradingAccountRepository.save(account);

        log.info("CREATE trading account '{}' id={} setup='{}' broker={} | user={}",
                accountName, account.getId(), setup.getLabel(), setup.getBroker().getCode(), email);
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

        // Moving an account to another setup would leave it authenticating with a
        // key that never issued it. Delete and recreate deliberately instead.
        if (request.getUserBrokerId() != null
                && !request.getUserBrokerId().equals(account.getUserBroker().getId())) {
            throw new ResourceConflictException(
                    "An account cannot move between broker setups. Create it under the other setup instead.");
        }

        if (request.getAccountName() != null) {
            String accountName = requireAccountName(request.getAccountName());
            if (!accountName.equals(account.getAccountName())
                    && tradingAccountRepository.existsByUserBroker_IdAndAccountName(
                            account.getUserBroker().getId(), accountName)) {
                throw new ResourceConflictException("Account '" + accountName + "' already exists under '"
                        + account.getUserBroker().getLabel() + "'");
            }
            account.setAccountName(accountName);
        }
        if (request.getBrokerAccountId() != null) {
            account.setBrokerAccountId(request.getBrokerAccountId().isEmpty() ? null
                    : trimTo(request.getBrokerAccountId(), BROKER_ACCOUNT_ID_MAX, "brokerAccountId"));
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
        return trimTo(accountName, ACCOUNT_NAME_MAX, "accountName");
    }

    private static String trimTo(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new StrategyValidationException(field + " must be at most " + max + " characters");
        }
        return trimmed;
    }

    private TradingAccountResponse toResponse(TradingAccount account) {
        UserBroker setup = account.getUserBroker();
        Broker broker = setup != null ? setup.getBroker() : null;

        BrokerCredential own = credentialRepository.findByTradingAccount_Id(account.getId()).orElse(null);
        BrokerCredential inherited = setup == null ? null
                : credentialRepository.findByUserBroker_IdAndTradingAccountIsNull(setup.getId()).orElse(null);

        // "Can authenticate" is true either way round: its own key, or the one it
        // inherits. Which of the two is a separate flag, not a separate answer.
        boolean configured = (own != null && own.hasAnyValue()) || (inherited != null && inherited.hasAnyValue());

        return new TradingAccountResponse(
                account.getId(),
                setup != null ? setup.getId() : null,
                setup != null ? setup.getLabel() : null,
                broker != null ? broker.getId() : null,
                broker != null ? broker.getCode() : null,
                broker != null ? broker.getName() : null,
                broker != null ? broker.getAuthType() : null,
                account.getAccountName(),
                account.getBrokerAccountId(),
                account.isActive(),
                configured,
                own != null && own.hasAnyValue(),
                subscriptionRepository.countByTradingAccount_IdAndActiveTrue(account.getId()),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}

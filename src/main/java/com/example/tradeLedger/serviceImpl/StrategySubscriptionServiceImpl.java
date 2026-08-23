package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.StrategySubscriptionRequest;
import com.example.tradeLedger.dto.StrategySubscriptionResponse;
import com.example.tradeLedger.dto.StrategySubscriptionUpdateRequest;
import com.example.tradeLedger.entity.RiskProfile;
import com.example.tradeLedger.entity.SharedStrategyConfig;
import com.example.tradeLedger.entity.StrategySubscription;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.entity.Symbol;
import com.example.tradeLedger.entity.TradingAccount;
import com.example.tradeLedger.entity.User;
import com.example.tradeLedger.entity.UserBroker;
import com.example.tradeLedger.entity.UserStrategy;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.RiskProfileRepository;
import com.example.tradeLedger.repository.StrategySubscriptionRepository;
import com.example.tradeLedger.repository.TradingAccountRepository;
import com.example.tradeLedger.repository.UserStrategyRepository;
import com.example.tradeLedger.service.CurrentUserService;
import com.example.tradeLedger.service.SharedStrategyConfigService;
import com.example.tradeLedger.service.StrategySubscriptionService;
import com.example.tradeLedger.utils.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deployments: which brokers a saved strategy is running on.
 *
 * Almost nothing is decided here any more. The configuration belongs to the
 * strategy and is reached through a foreign key, so this service only owns the
 * per-account facts - the account, the size multiplier, the risk profile, paper
 * or live - and the rule that a strategy is deployed on an account once.
 */
@Service
public class StrategySubscriptionServiceImpl implements StrategySubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(StrategySubscriptionServiceImpl.class);

    private static final Set<String> TRADE_MODES = Set.of(
            StrategySubscription.MODE_PAPER, StrategySubscription.MODE_LIVE);

    private static final Set<String> EXECUTION_MODES = Set.of(
            StrategySubscription.EXEC_FIXED_QTY, StrategySubscription.EXEC_CAPITAL_PERCENT,
            StrategySubscription.EXEC_RISK_PERCENT);

    private final CurrentUserService currentUserService;
    private final UserStrategyRepository userStrategyRepository;
    private final TradingAccountRepository tradingAccountRepository;
    private final RiskProfileRepository riskProfileRepository;
    private final StrategySubscriptionRepository subscriptionRepository;
    private final SharedStrategyConfigService sharedConfigService;
    private final UserStrategyValidator validator;
    private final RuleTrees ruleTrees;
    private final JsonSupport json;

    public StrategySubscriptionServiceImpl(CurrentUserService currentUserService,
                                           UserStrategyRepository userStrategyRepository,
                                           TradingAccountRepository tradingAccountRepository,
                                           RiskProfileRepository riskProfileRepository,
                                           StrategySubscriptionRepository subscriptionRepository,
                                           SharedStrategyConfigService sharedConfigService,
                                           UserStrategyValidator validator,
                                           RuleTrees ruleTrees,
                                           JsonSupport json) {
        this.currentUserService = currentUserService;
        this.userStrategyRepository = userStrategyRepository;
        this.tradingAccountRepository = tradingAccountRepository;
        this.riskProfileRepository = riskProfileRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.sharedConfigService = sharedConfigService;
        this.validator = validator;
        this.ruleTrees = ruleTrees;
        this.json = json;
    }

    // ----------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public List<StrategySubscriptionResponse> list(String email) {
        User user = currentUserService.require(email);
        return subscriptionRepository.findByUser_IdOrderByCreatedAtAsc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StrategySubscriptionResponse get(String email, UUID id) {
        return toResponse(requireOwned(currentUserService.require(email), id));
    }

    // --------------------------------------------------------------- create

    @Override
    @Transactional
    public StrategySubscriptionResponse create(String email, StrategySubscriptionRequest request) {
        User user = currentUserService.require(email);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        UserStrategy strategy = requireOwnedStrategy(user, request.getUserStrategyId());
        if (!strategy.isActive()) {
            throw new StrategyValidationException("Strategy " + strategy.getName()
                    + " is archived; reactivate it before deploying");
        }
        if (!strategy.isDeployable()) {
            throw new StrategyValidationException("Strategy " + strategy.getName()
                    + " has no market yet - set symbol and candleDuration before deploying");
        }
        TradingAccount account = requireOwnedAccount(user, request.getTradingAccountId());

        // UNIQUE (user_strategy_id, trading_account_id): the same strategy on the
        // same account is the same deployment, so a repeat is an edit.
        StrategySubscription existing = subscriptionRepository
                .findByUserStrategy_IdAndTradingAccount_Id(strategy.getId(), account.getId())
                .orElse(null);
        if (existing != null && existing.isActive()) {
            throw new ResourceConflictException("Strategy " + strategy.getName()
                    + " is already deployed on account " + account.getAccountName()
                    + " (subscriptionId=" + existing.getId()
                    + "). Change it with PUT /api/v1/my-subscriptions/" + existing.getId() + ".");
        }

        StrategySubscription subscription = existing != null ? existing : new StrategySubscription();
        subscription.setUser(user);
        subscription.setUserStrategy(strategy);
        subscription.setTradingAccount(account);
        subscription.setRiskProfile(resolveRiskProfile(request.getRiskProfileId()));
        subscription.setMultiplier(orDefault(request.getMultiplier(), BigDecimal.ONE));
        subscription.setCapitalAllocated(request.getCapitalAllocated());
        subscription.setExecutionMode(normalizeExecutionMode(request.getExecutionMode()));
        subscription.setTradeMode(normalizeTradeMode(request.getTradeMode()));
        subscription.setActive(true);
        subscriptionRepository.save(subscription);

        // A deployment is what keeps a shared computation scheduled, so the first
        // one on a retired config brings it back.
        sharedConfigService.reviveIfRetired(strategy.getSharedConfig());

        log.info("DEPLOY user={} strategy={} account={} mode={} instance={}",
                email, strategy.getName(), account.getAccountName(),
                subscription.getTradeMode(), strategy.getSharedConfig().getId());

        return toResponse(subscription);
    }

    // --------------------------------------------------------------- update

    @Override
    @Transactional
    public StrategySubscriptionResponse update(String email, UUID id,
                                               StrategySubscriptionUpdateRequest request) {
        User user = currentUserService.require(email);
        StrategySubscription subscription = requireOwned(user, id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        if (request.getMultiplier() != null) {
            subscription.setMultiplier(request.getMultiplier());
        }
        if (request.getCapitalAllocated() != null) {
            subscription.setCapitalAllocated(request.getCapitalAllocated());
        }
        if (request.getExecutionMode() != null) {
            subscription.setExecutionMode(normalizeExecutionMode(request.getExecutionMode()));
        }
        if (request.getTradeMode() != null) {
            subscription.setTradeMode(normalizeTradeMode(request.getTradeMode()));
        }
        if (request.getRiskProfileId() != null) {
            subscription.setRiskProfile(resolveRiskProfile(request.getRiskProfileId()));
        }

        boolean activeChanged = request.getActive() != null && request.getActive() != subscription.isActive();
        if (request.getActive() != null) {
            subscription.setActive(request.getActive());
        }
        subscriptionRepository.save(subscription);

        if (activeChanged) {
            SharedStrategyConfig config = subscription.getUserStrategy().getSharedConfig();
            if (subscription.isActive()) {
                sharedConfigService.reviveIfRetired(config);
            } else {
                subscriptionRepository.flush();
                sharedConfigService.retireIfOrphaned(config.getId());
            }
            log.info("{} user={} subscription={}", subscription.isActive() ? "RESUME" : "PAUSE", email, id);
        }
        return toResponse(subscription);
    }

    // --------------------------------------------------------------- delete

    @Override
    @Transactional
    public void delete(String email, UUID id) {
        User user = currentUserService.require(email);
        StrategySubscription subscription = requireOwned(user, id);
        SharedStrategyConfig config = subscription.getUserStrategy().getSharedConfig();

        subscriptionRepository.delete(subscription);
        subscriptionRepository.flush();
        if (config != null) {
            sharedConfigService.retireIfOrphaned(config.getId());
        }
        log.info("WITHDRAW user={} subscription={}", email, id);
    }

    // ------------------------------------------------------------ resolving

    private StrategySubscription requireOwned(User user, UUID id) {
        return subscriptionRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Subscription", id));
    }

    private UserStrategy requireOwnedStrategy(User user, UUID strategyId) {
        if (strategyId == null) {
            throw new StrategyValidationException("userStrategyId is required");
        }
        return userStrategyRepository.findByIdAndUser_Id(strategyId, user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Strategy", strategyId));
    }

    private TradingAccount requireOwnedAccount(User user, UUID accountId) {
        if (accountId == null) {
            throw new StrategyValidationException("tradingAccountId is required");
        }
        TradingAccount account = tradingAccountRepository.findByIdAndUser_Id(accountId, user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Trading account", accountId));
        if (!account.isActive()) {
            throw new StrategyValidationException("Trading account is not active: " + account.getAccountName());
        }
        return account;
    }

    private RiskProfile resolveRiskProfile(UUID riskProfileId) {
        if (riskProfileId == null) {
            return null;
        }
        return riskProfileRepository.findById(riskProfileId)
                .orElseThrow(() -> ResourceNotFoundException.of("Risk profile", riskProfileId));
    }

    private String normalizeTradeMode(String tradeMode) {
        if (tradeMode == null || tradeMode.isBlank()) {
            return StrategySubscription.MODE_PAPER;
        }
        String normalized = tradeMode.trim().toLowerCase(Locale.ROOT);
        if (!TRADE_MODES.contains(normalized)) {
            throw new StrategyValidationException("tradeMode must be one of " + TRADE_MODES);
        }
        return normalized;
    }

    private String normalizeExecutionMode(String executionMode) {
        if (executionMode == null || executionMode.isBlank()) {
            return StrategySubscription.EXEC_FIXED_QTY;
        }
        String normalized = executionMode.trim().toUpperCase(Locale.ROOT);
        if (!EXECUTION_MODES.contains(normalized)) {
            throw new StrategyValidationException("executionMode must be one of " + EXECUTION_MODES);
        }
        return normalized;
    }

    private BigDecimal orDefault(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }

    // -------------------------------------------------------------- mapping

    /**
     * The configuration fields are read THROUGH the strategy, not from a copy on
     * this row - which is why a retune shows up on every deployment at once.
     */
    private StrategySubscriptionResponse toResponse(StrategySubscription subscription) {
        UserStrategy strategy = subscription.getUserStrategy();
        StrategyTemplate template = strategy.getStrategy();
        Symbol symbol = strategy.getSymbol();
        SharedStrategyConfig config = strategy.getSharedConfig();
        TradingAccount account = subscription.getTradingAccount();
        UserBroker broker = account.getUserBroker();
        RiskProfile riskProfile = subscription.getRiskProfile();

        return new StrategySubscriptionResponse(
                subscription.getId(),
                subscription.getUser().getId(),
                strategy.getId(),
                strategy.getName(),
                template.getId(),
                template.getName(),
                symbol != null ? symbol.getId() : null,
                symbol != null ? symbol.getSymbol() : null,
                strategy.getCandleDuration(),
                strategy.getDerivative().name(),
                validator.legs(strategy),
                strategy.getLotRule().name(),
                strategy.getBaseLot(),
                strategy.getAveragingCount(),
                strategy.getSlPct(),
                strategy.getTpPct(),
                config != null ? config.getId() : null,
                config != null ? config.getConfigHash() : null,
                config != null ? json.toMap(config.getSignalParams()) : Map.of(),
                config != null ? ruleTrees.indicators(template, json.readTree(config.getSignalParams()))
                        : List.of(),
                account.getId(),
                account.getAccountName(),
                broker.getId(),
                broker.getLabel(),
                riskProfile != null ? riskProfile.getId() : null,
                riskProfile != null ? riskProfile.getName() : null,
                subscription.getMultiplier(),
                subscription.getCapitalAllocated(),
                subscription.getExecutionMode(),
                subscription.getTradeMode(),
                subscription.isActive(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt());
    }
}

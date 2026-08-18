package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.StrategySubscriptionRequest;
import com.example.tradeLedger.dto.StrategySubscriptionResponse;
import com.example.tradeLedger.dto.StrategySubscriptionUpdateRequest;
import com.example.tradeLedger.entity.*;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.*;
import com.example.tradeLedger.service.CurrentUserService;
import com.example.tradeLedger.service.SharedStrategyConfigService;
import com.example.tradeLedger.service.StrategySubscriptionService;
import com.example.tradeLedger.serviceImpl.StrategyParamValidator.ValidatedParams;
import com.example.tradeLedger.utils.CanonicalJson;
import com.example.tradeLedger.utils.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StrategySubscriptionServiceImpl implements StrategySubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(StrategySubscriptionServiceImpl.class);

    private static final Set<String> TRADE_MODES = Set.of(
            StrategySubscription.MODE_PAPER, StrategySubscription.MODE_LIVE);

    private static final Set<String> EXECUTION_MODES = Set.of(
            StrategySubscription.EXEC_FIXED_QTY, StrategySubscription.EXEC_CAPITAL_PERCENT,
            StrategySubscription.EXEC_RISK_PERCENT);

    private final CurrentUserService currentUserService;
    private final SharedStrategyConfigService sharedConfigService;
    private final StrategyTemplateRepository strategyRepository;
    private final StrategyParamDefinitionRepository paramDefRepository;
    private final SymbolResolver symbolResolver;
    private final TradingAccountRepository tradingAccountRepository;
    private final RiskProfileRepository riskProfileRepository;
    private final StrategySubscriptionRepository subscriptionRepository;
    private final StrategyParamValidator paramValidator;
    private final RuleTrees ruleTrees;
    private final JsonSupport json;

    public StrategySubscriptionServiceImpl(CurrentUserService currentUserService,
                                           SharedStrategyConfigService sharedConfigService,
                                           StrategyTemplateRepository strategyRepository,
                                           StrategyParamDefinitionRepository paramDefRepository,
                                           SymbolResolver symbolResolver,
                                           TradingAccountRepository tradingAccountRepository,
                                           RiskProfileRepository riskProfileRepository,
                                           StrategySubscriptionRepository subscriptionRepository,
                                           StrategyParamValidator paramValidator,
                                           RuleTrees ruleTrees,
                                           JsonSupport json) {
        this.currentUserService = currentUserService;
        this.sharedConfigService = sharedConfigService;
        this.strategyRepository = strategyRepository;
        this.paramDefRepository = paramDefRepository;
        this.symbolResolver = symbolResolver;
        this.tradingAccountRepository = tradingAccountRepository;
        this.riskProfileRepository = riskProfileRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paramValidator = paramValidator;
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
        User user = currentUserService.require(email);
        return toResponse(requireOwned(user, id));
    }

    // --------------------------------------------------------------- create

    @Override
    @Transactional
    public StrategySubscriptionResponse create(String email, StrategySubscriptionRequest request) {
        User user = currentUserService.require(email);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        StrategyTemplate strategy = resolveStrategy(request);
        if (!strategy.isActive()) {
            throw new StrategyValidationException("StrategyTemplate is not active: " + strategy.getName());
        }
        Symbol symbol = resolveSymbol(request);
        TradingAccount account = requireOwnedAccount(user, request.getTradingAccountId());
        RiskProfile riskProfile = resolveRiskProfile(request.getRiskProfileId());
        String timeframe = Timeframes.normalize(request.getTimeframe());
        String tradeMode = normalizeTradeMode(request.getTradeMode());
        String executionMode = normalizeExecutionMode(request.getExecutionMode());

        ValidatedParams params = validateParams(strategy, request.getParams());
        assertRuleTreeResolves(strategy, params.getSignal());

        SharedStrategyConfigService.Resolution resolution =
                sharedConfigService.resolveOrCreate(strategy, symbol, timeframe, params.getSignal());
        SharedStrategyConfig instance = resolution.instance();

        // UNIQUE (shared_config_id, trading_account_id): the same math on the
        // same account is the same leg, so it is an update, not a second row.
        StrategySubscription existing = subscriptionRepository
                .findBySharedConfig_IdAndTradingAccount_Id(instance.getId(), account.getId())
                .orElse(null);
        if (existing != null && existing.isActive()) {
            throw new ResourceConflictException("Already subscribed to this exact configuration on account '"
                    + account.getAccountName() + "' (subscriptionId=" + existing.getId()
                    + "). Change it with PUT /api/v1/my-subscriptions/" + existing.getId() + ".");
        }

        StrategySubscription subscription = existing != null ? existing : new StrategySubscription();
        subscription.setUser(user);
        subscription.setSharedConfig(instance);
        subscription.setTradingAccount(account);
        subscription.setRiskProfile(riskProfile);
        subscription.setQuantity(orDefault(request.getQuantity(), BigDecimal.ONE));
        subscription.setMultiplier(orDefault(request.getMultiplier(), BigDecimal.ONE));
        subscription.setLotSize(request.getLotSize());
        subscription.setCapitalAllocated(request.getCapitalAllocated());
        subscription.setExecutionMode(executionMode);
        subscription.setExecParams(json.toJson(params.getExecution()));
        subscription.setTradeMode(tradeMode);
        subscription.setActive(true);
        subscriptionRepository.save(subscription);

        log.info("SUBSCRIBE user={} strategy='{}' {} {} signal=[{}] exec=[{}] instance={} ({}) hash={}",
                email, strategy.getName(), symbol.getSymbol(), timeframe,
                CanonicalJson.describe(params.getSignal()), CanonicalJson.describe(params.getExecution()),
                instance.getId(), resolution.created() ? "new" : "shared", instance.getConfigHash());

        return toResponse(subscription);
    }

    // --------------------------------------------------------------- update

    @Override
    @Transactional
    public StrategySubscriptionResponse update(String email, UUID id, StrategySubscriptionUpdateRequest request) {
        User user = currentUserService.require(email);
        StrategySubscription subscription = requireOwned(user, id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        SharedStrategyConfig current = subscription.getSharedConfig();
        StrategyTemplate strategy = current.getStrategy();

        // Submitted keys are merged over the current effective config, so a body
        // of {"fast": 13} is valid and leaves every other knob untouched.
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.putAll(json.toMap(current.getSignalParams()));
        merged.putAll(json.toMap(subscription.getExecParams()));
        if (request.getParams() != null) {
            merged.putAll(request.getParams());
        }

        ValidatedParams params = validateParams(strategy, merged);
        assertRuleTreeResolves(strategy, params.getSignal());

        if (request.getExecutionMode() != null) {
            subscription.setExecutionMode(normalizeExecutionMode(request.getExecutionMode()));
        }
        if (request.getTradeMode() != null) {
            subscription.setTradeMode(normalizeTradeMode(request.getTradeMode()));
        }
        if (request.getQuantity() != null) {
            subscription.setQuantity(request.getQuantity());
        }
        if (request.getMultiplier() != null) {
            subscription.setMultiplier(request.getMultiplier());
        }
        if (request.getLotSize() != null) {
            subscription.setLotSize(request.getLotSize());
        }
        if (request.getCapitalAllocated() != null) {
            subscription.setCapitalAllocated(request.getCapitalAllocated());
        }
        if (request.getRiskProfileId() != null) {
            subscription.setRiskProfile(resolveRiskProfile(request.getRiskProfileId()));
        }
        subscription.setExecParams(json.toJson(params.getExecution()));

        SharedStrategyConfigService.Resolution resolution = sharedConfigService.resolveOrCreate(
                strategy, current.getSymbol(), current.getTimeframe(), params.getSignal());
        SharedStrategyConfig target = resolution.instance();
        boolean repointed = !target.getId().equals(current.getId());

        if (repointed) {
            UUID accountId = subscription.getTradingAccount().getId();
            subscriptionRepository.findBySharedConfig_IdAndTradingAccount_Id(target.getId(), accountId)
                    .filter(other -> !other.getId().equals(subscription.getId()))
                    .ifPresent(other -> {
                        throw new ResourceConflictException("Account '"
                                + subscription.getTradingAccount().getAccountName()
                                + "' is already subscribed to that configuration (subscriptionId="
                                + other.getId() + ")");
                    });

            if (resolution.created() && target.getSupersedes() == null) {
                target.setSupersedes(current);
            }
            subscription.setSharedConfig(target);
            subscription.setVersion(subscription.getVersion() + 1);
        }

        boolean activeChanged = request.getActive() != null && request.getActive() != subscription.isActive();
        if (request.getActive() != null) {
            subscription.setActive(request.getActive());
        }
        subscriptionRepository.save(subscription);

        if (repointed) {
            sharedConfigService.retireIfOrphaned(current.getId());
            log.info("REPOINT user={} subscription={} instance {} -> {} ({}) signal=[{}]",
                    email, id, current.getId(), target.getId(),
                    resolution.created() ? "new" : "shared", CanonicalJson.describe(params.getSignal()));
        }
        if (activeChanged) {
            if (subscription.isActive()) {
                sharedConfigService.reviveIfRetired(subscription.getSharedConfig());
            } else {
                sharedConfigService.retireIfOrphaned(subscription.getSharedConfig().getId());
            }
            log.info("{} user={} subscription={}", subscription.isActive() ? "ACTIVATE" : "PAUSE", email, id);
        }
        if (!repointed && !activeChanged) {
            log.info("UPDATE user={} subscription={} exec=[{}]",
                    email, id, CanonicalJson.describe(params.getExecution()));
        }

        return toResponse(subscription);
    }

    // --------------------------------------------------------------- delete

    @Override
    @Transactional
    public void delete(String email, UUID id) {
        User user = currentUserService.require(email);
        StrategySubscription subscription = requireOwned(user, id);
        UUID instanceId = subscription.getSharedConfig().getId();

        subscriptionRepository.delete(subscription);
        subscriptionRepository.flush();
        sharedConfigService.retireIfOrphaned(instanceId);
        log.info("UNSUBSCRIBE user={} subscription={}", email, id);
    }

    // ------------------------------------------------------------ resolving

    private StrategySubscription requireOwned(User user, UUID id) {
        return subscriptionRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Subscription", id));
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

    private StrategyTemplate resolveStrategy(StrategySubscriptionRequest request) {
        if (request.getStrategyId() != null) {
            return strategyRepository.findById(request.getStrategyId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Strategy template", request.getStrategyId()));
        }
        if (request.getStrategyName() != null && !request.getStrategyName().isBlank()) {
            String name = request.getStrategyName().trim();
            return strategyRepository.findByName(name)
                    .orElseThrow(() -> ResourceNotFoundException.of("Strategy template", name));
        }
        throw new StrategyValidationException("strategyId or strategyName is required");
    }

    private Symbol resolveSymbol(StrategySubscriptionRequest request) {
        return symbolResolver.require(request.getSymbolId(), request.getSymbol(), request.getExchangeCode());
    }

    private RiskProfile resolveRiskProfile(UUID riskProfileId) {
        if (riskProfileId == null) {
            return null;
        }
        return riskProfileRepository.findById(riskProfileId)
                .orElseThrow(() -> ResourceNotFoundException.of("Risk profile", riskProfileId));
    }

    // ----------------------------------------------------------- validation

    private ValidatedParams validateParams(StrategyTemplate strategy, Map<String, Object> submitted) {
        List<StrategyParamDefinition> defs =
                paramDefRepository.findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(strategy.getId());
        return paramValidator.validate(defs, submitted);
    }

    private void assertRuleTreeResolves(StrategyTemplate strategy, Map<String, Object> signalParams) {
        ruleTrees.assertResolves(strategy, signalParams);
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

    private StrategySubscriptionResponse toResponse(StrategySubscription subscription) {
        SharedStrategyConfig instance = subscription.getSharedConfig();
        StrategyTemplate strategy = instance.getStrategy();
        Symbol symbol = instance.getSymbol();
        TradingAccount account = subscription.getTradingAccount();
        RiskProfile riskProfile = subscription.getRiskProfile();

        return new StrategySubscriptionResponse(
                subscription.getId(),
                subscription.getUser().getId(),
                strategy.getId(),
                strategy.getName(),
                instance.getId(),
                instance.getConfigHash(),
                symbol.getId(),
                symbol.getSymbol(),
                instance.getTimeframe(),
                json.toMap(instance.getSignalParams()),
                json.toMap(subscription.getExecParams()),
                resolveIndicators(strategy, instance),
                account.getId(),
                account.getAccountName(),
                riskProfile != null ? riskProfile.getId() : null,
                riskProfile != null ? riskProfile.getName() : null,
                subscription.getQuantity(),
                subscription.getMultiplier(),
                subscription.getLotSize(),
                subscription.getCapitalAllocated(),
                subscription.getExecutionMode(),
                subscription.getTradeMode(),
                subscription.isActive(),
                subscription.getVersion(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt());
    }

    private List<String> resolveIndicators(StrategyTemplate strategy, SharedStrategyConfig sharedConfig) {
        return ruleTrees.indicators(strategy, json.readTree(sharedConfig.getSignalParams()));
    }
}

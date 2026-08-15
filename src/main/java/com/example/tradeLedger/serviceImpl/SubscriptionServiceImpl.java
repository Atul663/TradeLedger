package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.SubscriptionRequest;
import com.example.tradeLedger.dto.SubscriptionResponse;
import com.example.tradeLedger.dto.SubscriptionUpdateRequest;
import com.example.tradeLedger.entity.*;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.repository.*;
import com.example.tradeLedger.service.CurrentUserService;
import com.example.tradeLedger.service.StrategyInstanceService;
import com.example.tradeLedger.service.SubscriptionService;
import com.example.tradeLedger.serviceImpl.StrategyParamValidator.ValidatedParams;
import com.example.tradeLedger.utils.CanonicalJson;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionServiceImpl.class);

    /** '30s', '5m', '15m', '1h', '1d', '1w' - the shape the design's timeframes take. */
    private static final Pattern TIMEFRAME = Pattern.compile("^[0-9]{1,4}[smhdw]$");

    private static final Set<String> TRADE_MODES = Set.of(Subscription.MODE_PAPER, Subscription.MODE_LIVE);

    private static final Set<String> EXECUTION_MODES = Set.of(
            Subscription.EXEC_FIXED_QTY, Subscription.EXEC_CAPITAL_PERCENT, Subscription.EXEC_RISK_PERCENT);

    private final CurrentUserService currentUserService;
    private final StrategyInstanceService instanceService;
    private final StrategyRepository strategyRepository;
    private final StrategyParamDefRepository paramDefRepository;
    private final SymbolRepository symbolRepository;
    private final ExchangeRepository exchangeRepository;
    private final TradingAccountRepository tradingAccountRepository;
    private final RiskProfileRepository riskProfileRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StrategyParamValidator paramValidator;
    private final JsonSupport json;

    public SubscriptionServiceImpl(CurrentUserService currentUserService,
                                   StrategyInstanceService instanceService,
                                   StrategyRepository strategyRepository,
                                   StrategyParamDefRepository paramDefRepository,
                                   SymbolRepository symbolRepository,
                                   ExchangeRepository exchangeRepository,
                                   TradingAccountRepository tradingAccountRepository,
                                   RiskProfileRepository riskProfileRepository,
                                   SubscriptionRepository subscriptionRepository,
                                   StrategyParamValidator paramValidator,
                                   JsonSupport json) {
        this.currentUserService = currentUserService;
        this.instanceService = instanceService;
        this.strategyRepository = strategyRepository;
        this.paramDefRepository = paramDefRepository;
        this.symbolRepository = symbolRepository;
        this.exchangeRepository = exchangeRepository;
        this.tradingAccountRepository = tradingAccountRepository;
        this.riskProfileRepository = riskProfileRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paramValidator = paramValidator;
        this.json = json;
    }

    // ----------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> list(String email) {
        User user = currentUserService.require(email);
        return subscriptionRepository.findByUser_IdOrderByCreatedAtAsc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse get(String email, UUID id) {
        User user = currentUserService.require(email);
        return toResponse(requireOwned(user, id));
    }

    // --------------------------------------------------------------- create

    @Override
    @Transactional
    public SubscriptionResponse create(String email, SubscriptionRequest request) {
        User user = currentUserService.require(email);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        Strategy strategy = resolveStrategy(request);
        if (!strategy.isActive()) {
            throw new StrategyValidationException("Strategy is not active: " + strategy.getName());
        }
        Symbol symbol = resolveSymbol(request);
        TradingAccount account = requireOwnedAccount(user, request.getTradingAccountId());
        RiskProfile riskProfile = resolveRiskProfile(request.getRiskProfileId());
        String timeframe = normalizeTimeframe(request.getTimeframe());
        String tradeMode = normalizeTradeMode(request.getTradeMode());
        String executionMode = normalizeExecutionMode(request.getExecutionMode());

        ValidatedParams params = validateParams(strategy, request.getParams());
        assertRuleTreeResolves(strategy, params.getSignal());

        StrategyInstanceService.Resolution resolution =
                instanceService.resolveOrCreate(strategy, symbol, timeframe, params.getSignal());
        StrategyInstance instance = resolution.instance();

        // UNIQUE (strategy_instance_id, trading_account_id): the same math on the
        // same account is the same leg, so it is an update, not a second row.
        Subscription existing = subscriptionRepository
                .findByStrategyInstance_IdAndTradingAccount_Id(instance.getId(), account.getId())
                .orElse(null);
        if (existing != null && existing.isActive()) {
            throw new ResourceConflictException("Already subscribed to this exact configuration on account '"
                    + account.getAccountName() + "' (subscriptionId=" + existing.getId()
                    + "). Change it with PUT /api/v1/subscriptions/" + existing.getId() + ".");
        }

        Subscription subscription = existing != null ? existing : new Subscription();
        subscription.setUser(user);
        subscription.setStrategyInstance(instance);
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
    public SubscriptionResponse update(String email, UUID id, SubscriptionUpdateRequest request) {
        User user = currentUserService.require(email);
        Subscription subscription = requireOwned(user, id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        StrategyInstance current = subscription.getStrategyInstance();
        Strategy strategy = current.getStrategy();

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

        StrategyInstanceService.Resolution resolution = instanceService.resolveOrCreate(
                strategy, current.getSymbol(), current.getTimeframe(), params.getSignal());
        StrategyInstance target = resolution.instance();
        boolean repointed = !target.getId().equals(current.getId());

        if (repointed) {
            UUID accountId = subscription.getTradingAccount().getId();
            subscriptionRepository.findByStrategyInstance_IdAndTradingAccount_Id(target.getId(), accountId)
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
            subscription.setStrategyInstance(target);
            subscription.setVersion(subscription.getVersion() + 1);
        }

        boolean activeChanged = request.getActive() != null && request.getActive() != subscription.isActive();
        if (request.getActive() != null) {
            subscription.setActive(request.getActive());
        }
        subscriptionRepository.save(subscription);

        if (repointed) {
            instanceService.retireIfOrphaned(current.getId());
            log.info("REPOINT user={} subscription={} instance {} -> {} ({}) signal=[{}]",
                    email, id, current.getId(), target.getId(),
                    resolution.created() ? "new" : "shared", CanonicalJson.describe(params.getSignal()));
        }
        if (activeChanged) {
            if (subscription.isActive()) {
                instanceService.reviveIfRetired(subscription.getStrategyInstance());
            } else {
                instanceService.retireIfOrphaned(subscription.getStrategyInstance().getId());
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
        Subscription subscription = requireOwned(user, id);
        UUID instanceId = subscription.getStrategyInstance().getId();

        subscriptionRepository.delete(subscription);
        subscriptionRepository.flush();
        instanceService.retireIfOrphaned(instanceId);
        log.info("UNSUBSCRIBE user={} subscription={}", email, id);
    }

    // ------------------------------------------------------------ resolving

    private Subscription requireOwned(User user, UUID id) {
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

    private Strategy resolveStrategy(SubscriptionRequest request) {
        if (request.getStrategyId() != null) {
            return strategyRepository.findById(request.getStrategyId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Strategy", request.getStrategyId()));
        }
        if (request.getStrategyName() != null && !request.getStrategyName().isBlank()) {
            String name = request.getStrategyName().trim();
            return strategyRepository.findByName(name)
                    .orElseThrow(() -> ResourceNotFoundException.of("Strategy", name));
        }
        throw new StrategyValidationException("strategyId or strategyName is required");
    }

    private Symbol resolveSymbol(SubscriptionRequest request) {
        Symbol symbol;
        if (request.getSymbolId() != null) {
            symbol = symbolRepository.findById(request.getSymbolId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Symbol", request.getSymbolId()));
        } else if (request.getSymbol() != null && !request.getSymbol().isBlank()) {
            if (request.getExchangeCode() == null || request.getExchangeCode().isBlank()) {
                throw new StrategyValidationException(
                        "exchangeCode is required when identifying a symbol by name - symbols are unique per exchange");
            }
            String code = request.getExchangeCode().trim().toUpperCase(Locale.ROOT);
            Exchange exchange = exchangeRepository.findByCode(code)
                    .orElseThrow(() -> ResourceNotFoundException.of("Exchange", code));
            String name = request.getSymbol().trim().toUpperCase(Locale.ROOT);
            symbol = symbolRepository.findByExchange_IdAndSymbol(exchange.getId(), name)
                    .orElseThrow(() -> ResourceNotFoundException.of("Symbol", code + ":" + name));
        } else {
            throw new StrategyValidationException("symbolId, or symbol + exchangeCode, is required");
        }
        if (!symbol.isActive()) {
            throw new StrategyValidationException("Symbol is not active: " + symbol.getSymbol());
        }
        return symbol;
    }

    private RiskProfile resolveRiskProfile(UUID riskProfileId) {
        if (riskProfileId == null) {
            return null;
        }
        return riskProfileRepository.findById(riskProfileId)
                .orElseThrow(() -> ResourceNotFoundException.of("Risk profile", riskProfileId));
    }

    // ----------------------------------------------------------- validation

    private ValidatedParams validateParams(Strategy strategy, Map<String, Object> submitted) {
        List<StrategyParamDef> defs =
                paramDefRepository.findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(strategy.getId());
        return paramValidator.validate(defs, submitted);
    }

    /**
     * The knobs can be individually valid and still not satisfy the rule tree, if
     * the tree binds a {@code $key} that has no signal-scope parameter behind it.
     * Catching that here turns a runtime failure in the engine into a 400 on the
     * request that would have caused it.
     */
    private void assertRuleTreeResolves(Strategy strategy, Map<String, Object> signalParams) {
        JsonNode ruleTree = json.readTree(strategy.getRuleTree());
        if (ruleTree == null) {
            throw new StrategyValidationException(
                    "Strategy '" + strategy.getName() + "' has an unreadable rule tree");
        }
        try {
            IndicatorResolver.resolve(ruleTree, json.toNode(signalParams));
        } catch (RuntimeException e) {
            throw new StrategyValidationException(e.getMessage());
        }
    }

    private String normalizeTimeframe(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            throw new StrategyValidationException("timeframe is required");
        }
        String normalized = timeframe.trim().toLowerCase(Locale.ROOT);
        if (!TIMEFRAME.matcher(normalized).matches()) {
            throw new StrategyValidationException(
                    "timeframe must look like 30s / 5m / 15m / 1h / 1d / 1w, got '" + timeframe + "'");
        }
        return normalized;
    }

    private String normalizeTradeMode(String tradeMode) {
        if (tradeMode == null || tradeMode.isBlank()) {
            return Subscription.MODE_PAPER;
        }
        String normalized = tradeMode.trim().toLowerCase(Locale.ROOT);
        if (!TRADE_MODES.contains(normalized)) {
            throw new StrategyValidationException("tradeMode must be one of " + TRADE_MODES);
        }
        return normalized;
    }

    private String normalizeExecutionMode(String executionMode) {
        if (executionMode == null || executionMode.isBlank()) {
            return Subscription.EXEC_FIXED_QTY;
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

    private SubscriptionResponse toResponse(Subscription subscription) {
        StrategyInstance instance = subscription.getStrategyInstance();
        Strategy strategy = instance.getStrategy();
        Symbol symbol = instance.getSymbol();
        TradingAccount account = subscription.getTradingAccount();
        RiskProfile riskProfile = subscription.getRiskProfile();

        return new SubscriptionResponse(
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

    private List<String> resolveIndicators(Strategy strategy, StrategyInstance instance) {
        JsonNode ruleTree = json.readTree(strategy.getRuleTree());
        if (ruleTree == null) {
            return List.of();
        }
        try {
            return new ArrayList<>(
                    IndicatorResolver.resolve(ruleTree, json.readTree(instance.getSignalParams())));
        } catch (RuntimeException e) {
            log.warn("Could not resolve indicators for instance {}: {}", instance.getId(), e.getMessage());
            return List.of();
        }
    }
}

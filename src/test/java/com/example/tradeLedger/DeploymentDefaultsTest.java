package com.example.tradeLedger;

import com.example.tradeLedger.dto.StrategyDeployRequest;
import com.example.tradeLedger.dto.StrategySubscriptionRequest;
import com.example.tradeLedger.entity.Derivative;
import com.example.tradeLedger.entity.Exchange;
import com.example.tradeLedger.entity.SharedStrategyConfig;
import com.example.tradeLedger.entity.StrategySubscription;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.entity.Symbol;
import com.example.tradeLedger.entity.TradingAccount;
import com.example.tradeLedger.entity.User;
import com.example.tradeLedger.entity.UserBroker;
import com.example.tradeLedger.entity.UserStrategy;
import com.example.tradeLedger.repository.IndicatorRepository;
import com.example.tradeLedger.repository.SharedStrategyConfigRepository;
import com.example.tradeLedger.repository.StrategySubscriptionRepository;
import com.example.tradeLedger.repository.StrategyTemplateRepository;
import com.example.tradeLedger.repository.TradingAccountRepository;
import com.example.tradeLedger.repository.UserBrokerRepository;
import com.example.tradeLedger.repository.UserStrategyIndicatorRepository;
import com.example.tradeLedger.repository.UserStrategyRepository;
import com.example.tradeLedger.service.CurrentUserService;
import com.example.tradeLedger.service.SharedStrategyConfigService;
import com.example.tradeLedger.serviceImpl.IndicatorParams;
import com.example.tradeLedger.serviceImpl.RuleTrees;
import com.example.tradeLedger.serviceImpl.SubscriptionFanOut;
import com.example.tradeLedger.serviceImpl.SymbolResolver;
import com.example.tradeLedger.serviceImpl.UserStrategyServiceImpl;
import com.example.tradeLedger.serviceImpl.UserStrategyValidator;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The deployment defaults a strategy carries, and what a deploy does with them.
 *
 * These four - executionMode, multiplier, capitalAllocated, tradeMode - are the
 * only fixed parameters that name a column on BOTH tables. On the strategy they
 * say how a deployment of it should START; on the deployment they are what that
 * account actually runs. So the thing worth pinning is the direction: they flow
 * strategy to deployment, once, at deploy time, and never back.
 *
 * The resolution is narrowest-first - this account, then the call, then the
 * strategy - so a call that names nothing still deploys the way its author meant,
 * and a call that names something still wins.
 */
class DeploymentDefaultsTest {

    private static final String EMAIL = "trader@example.com";
    private static final UUID USER_ID = UUID.fromString("00000000-1111-4222-8333-444444444444");
    private static final UUID STRATEGY_ID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID ACCOUNT_ID = UUID.fromString("aa000000-1111-4222-8333-444444444444");

    private UserStrategy strategy;
    private SubscriptionFanOut fanOut;
    private UserStrategyServiceImpl service;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(USER_ID);

        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.require(EMAIL)).thenReturn(user);

        StrategyTemplate template = new StrategyTemplate();
        template.setId(UUID.randomUUID());
        template.setName("EMA Averaging");

        Exchange exchange = new Exchange();
        exchange.setId(UUID.randomUUID());
        exchange.setCode("NSE");

        Symbol symbol = new Symbol();
        symbol.setId(UUID.randomUUID());
        symbol.setSymbol("NIFTY");
        symbol.setExchange(exchange);

        SharedStrategyConfig config = new SharedStrategyConfig();
        config.setId(UUID.randomUUID());
        config.setConfigHash("6b1f0c9e");

        // Deployable: active, with a market. The defaults below are what this
        // strategy was authored to deploy as.
        strategy = new UserStrategy();
        strategy.setId(STRATEGY_ID);
        strategy.setUser(user);
        strategy.setStrategy(template);
        strategy.setName("NIFTY 21/9");
        strategy.setSymbol(symbol);
        strategy.setCandleDuration("5m");
        strategy.setDerivative(Derivative.OPTION);
        strategy.setSharedConfig(config);
        strategy.setExecutionMode(StrategySubscription.EXEC_CAPITAL_PERCENT);
        strategy.setMultiplier(new BigDecimal("2"));
        strategy.setCapitalAllocated(new BigDecimal("500000"));
        strategy.setTradeMode(StrategySubscription.MODE_LIVE);

        UserStrategyRepository strategies = mock(UserStrategyRepository.class);
        when(strategies.findByIdAndUser_Id(STRATEGY_ID, USER_ID)).thenReturn(Optional.of(strategy));

        UserBroker broker = new UserBroker();
        broker.setId(UUID.randomUUID());
        broker.setLabel("My Dhan");

        TradingAccount account = new TradingAccount();
        account.setId(ACCOUNT_ID);
        account.setAccountName("main");
        account.setUserBroker(broker);

        TradingAccountRepository accounts = mock(TradingAccountRepository.class);
        when(accounts.findByIdAndUser_Id(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(account));

        fanOut = mock(SubscriptionFanOut.class);

        service = new UserStrategyServiceImpl(
                currentUser,
                strategies,
                mock(UserStrategyIndicatorRepository.class),
                mock(StrategyTemplateRepository.class),
                mock(IndicatorRepository.class),
                mock(StrategySubscriptionRepository.class),
                mock(SharedStrategyConfigRepository.class),
                accounts,
                mock(UserBrokerRepository.class),
                mock(SharedStrategyConfigService.class),
                fanOut,
                mock(IndicatorParams.class),
                new UserStrategyValidator(),
                mock(SymbolResolver.class),
                mock(RuleTrees.class),
                new JsonSupport(new ObjectMapper()));
    }

    /** A deploy naming only where to go. */
    private static StrategyDeployRequest bareRequest() {
        StrategyDeployRequest.Target target = new StrategyDeployRequest.Target();
        target.setTradingAccountId(ACCOUNT_ID);
        StrategyDeployRequest request = new StrategyDeployRequest();
        request.setTargets(List.of(target));
        return request;
    }

    /** What the fan-out was actually asked to create. */
    private StrategySubscriptionRequest deployed() {
        ArgumentCaptor<StrategySubscriptionRequest> captor =
                ArgumentCaptor.forClass(StrategySubscriptionRequest.class);
        verify(fanOut).deployOne(eq(EMAIL), captor.capture());
        return captor.getValue();
    }

    /**
     * The point of the whole feature: "deploy it on my Dhan" carries the author's
     * intent without repeating it on every call.
     */
    @Test
    void aDeployThatNamesNothingInheritsTheStrategysDefaults() {
        service.deploy(EMAIL, STRATEGY_ID, bareRequest());

        StrategySubscriptionRequest request = deployed();
        assertEquals(StrategySubscription.EXEC_CAPITAL_PERCENT, request.getExecutionMode());
        assertEquals(new BigDecimal("2"), request.getMultiplier());
        assertEquals(new BigDecimal("500000"), request.getCapitalAllocated());
        assertEquals(StrategySubscription.MODE_LIVE, request.getTradeMode());
    }

    /** The call still wins over the strategy - the defaults are a floor, not a lock. */
    @Test
    void theCallOverridesTheStrategy() {
        StrategyDeployRequest request = bareRequest();
        request.setTradeMode(StrategySubscription.MODE_PAPER);
        request.setMultiplier(BigDecimal.ONE);

        service.deploy(EMAIL, STRATEGY_ID, request);

        assertEquals(StrategySubscription.MODE_PAPER, deployed().getTradeMode());
        assertEquals(BigDecimal.ONE, deployed().getMultiplier());
        assertEquals(StrategySubscription.EXEC_CAPITAL_PERCENT, deployed().getExecutionMode(),
                "what the call did not name still comes from the strategy");
    }

    /** And the account wins over the call - narrowest first, all three levels. */
    @Test
    void theTargetOverridesTheCallWhichOverridesTheStrategy() {
        StrategyDeployRequest.Target target = new StrategyDeployRequest.Target();
        target.setTradingAccountId(ACCOUNT_ID);
        target.setMultiplier(new BigDecimal("3"));

        StrategyDeployRequest request = new StrategyDeployRequest();
        request.setTargets(List.of(target));
        request.setMultiplier(BigDecimal.ONE);

        service.deploy(EMAIL, STRATEGY_ID, request);

        assertEquals(new BigDecimal("3"), deployed().getMultiplier(), "the account's own value");
        assertEquals(StrategySubscription.MODE_LIVE, deployed().getTradeMode(),
                "and the strategy still supplies what neither named");
    }

    /**
     * A null default is a real answer, not a missing one: nothing earmarked stays
     * nothing earmarked rather than falling through to some other number.
     */
    @Test
    void anUnsetCapitalStaysUnset() {
        strategy.setCapitalAllocated(null);

        service.deploy(EMAIL, STRATEGY_ID, bareRequest());

        assertNull(deployed().getCapitalAllocated());
    }

    /**
     * A strategy created without naming any of them deploys to paper at 1x.
     *
     * This is the safety property: a field left unset must never be the reason an
     * order reaches a broker with real money behind it.
     */
    @Test
    void afreshStrategyDefaultsToPaperAtOneTimes() {
        UserStrategy fresh = new UserStrategy();

        assertEquals(StrategySubscription.MODE_PAPER, fresh.getTradeMode());
        assertEquals(BigDecimal.ONE, fresh.getMultiplier());
        assertEquals(StrategySubscription.EXEC_FIXED_QTY, fresh.getExecutionMode());
        assertNull(fresh.getCapitalAllocated());
    }

    /** The enums are normalized to the casing a deployment stores, not echoed back raw. */
    @Test
    void theModesAreNormalizedToWhatADeploymentHolds() {
        assertEquals(StrategySubscription.EXEC_RISK_PERCENT,
                UserStrategyValidator.executionMode("risk_percent"));
        assertEquals(StrategySubscription.MODE_LIVE, UserStrategyValidator.tradeMode("LIVE"));
        assertNull(UserStrategyValidator.tradeMode(null), "absent is left alone, not defaulted");
        assertNull(UserStrategyValidator.tradeMode("  "));
    }

    @Test
    void anUnknownModeIsRefusedWithItsAlternatives() {
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                com.example.tradeLedger.exception.StrategyValidationException.class,
                () -> UserStrategyValidator.tradeMode("demo"));
        org.junit.jupiter.api.Assertions.assertTrue(thrown.getMessage().contains("paper"),
                thrown.getMessage());
    }

    /** A negative size would reach the broker as a real order, so it is refused. */
    @Test
    void aNegativeMultiplierIsRefused() {
        UserStrategy bad = new UserStrategy();
        bad.setDerivative(Derivative.FUTURES);
        bad.setMultiplier(new BigDecimal("-1"));

        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                com.example.tradeLedger.exception.StrategyValidationException.class,
                () -> new UserStrategyValidator().validate(bad));
        org.junit.jupiter.api.Assertions.assertTrue(thrown.getMessage().contains("multiplier"),
                thrown.getMessage());
    }

    /** Unused stub guard: the fan-out is the only thing the deploy path calls out to. */
    @Test
    void theDeployPathTouchesNothingElse() {
        service.deploy(EMAIL, STRATEGY_ID, bareRequest());
        verify(fanOut).deployOne(eq(EMAIL), any(StrategySubscriptionRequest.class));
    }
}

package com.example.tradeLedger;

import com.example.tradeLedger.dto.UserStrategyIndicatorGroupResponse;
import com.example.tradeLedger.dto.UserStrategyIndicatorResponse;
import com.example.tradeLedger.dto.UserStrategyResponse;
import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.entity.User;
import com.example.tradeLedger.entity.UserStrategy;
import com.example.tradeLedger.entity.UserStrategyIndicator;
import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.repository.IndicatorRepository;
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
import com.example.tradeLedger.serviceImpl.StrategyFixedParameters;
import com.example.tradeLedger.serviceImpl.SubscriptionFanOut;
import com.example.tradeLedger.serviceImpl.SymbolResolver;
import com.example.tradeLedger.serviceImpl.UserStrategyServiceImpl;
import com.example.tradeLedger.serviceImpl.UserStrategyValidator;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A strategy's indicator tuning, arranged one section per indicator name.
 *
 * The arrangement only earns its place when a template names the same indicator
 * more than once - two EMAs on different periods, an RSI filter per side. Flat,
 * those read as repeating look-alike rows; grouped, they read as one section with
 * its usages under it. So what is worth pinning is that repeats actually land
 * together, that the grouped shape holds exactly the rows the flat list does, and
 * that the group's tag and schema describe the indicator rather than one usage
 * of it.
 */
class IndicatorGroupingTest {

    private static final String EMAIL = "trader@example.com";
    private static final UUID USER_ID = UUID.fromString("00000000-1111-4222-8333-444444444444");
    private static final UUID STRATEGY_ID = UUID.fromString("11111111-2222-4333-8444-555555555555");

    private static final UUID EMA_ID = UUID.fromString("b2e4a1c8-1f3d-4e6a-9b2c-5d7e8f0a1b2c");
    private static final UUID RSI_ID = UUID.fromString("c3f5b2d9-2e4c-4f7b-8a3d-6e8f9a0b1c2d");

    private static final String EMA_SCHEMA = """
            {"period":{"type":"int","min":1,"max":300,"default":21}}""";

    private UserStrategyIndicatorRepository indicatorRows;
    private UserStrategyRepository strategies;
    private UserStrategyServiceImpl service;
    private User user;
    private UserStrategy strategy;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);

        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.require(EMAIL)).thenReturn(user);

        StrategyTemplate template = new StrategyTemplate();
        template.setId(UUID.randomUUID());
        template.setName("EMA Averaging");

        strategy = new UserStrategy();
        strategy.setId(STRATEGY_ID);
        strategy.setUser(user);
        strategy.setStrategy(template);
        strategy.setName("NIFTY 21/9");

        strategies = mock(UserStrategyRepository.class);
        when(strategies.findByIdAndUser_Id(STRATEGY_ID, USER_ID))
                .thenReturn(java.util.Optional.of(strategy));

        indicatorRows = mock(UserStrategyIndicatorRepository.class);

        StrategyFixedParameters fixedParameters = mock(StrategyFixedParameters.class);
        when(fixedParameters.forStrategy(any())).thenReturn(List.of());

        service = new UserStrategyServiceImpl(
                currentUser,
                strategies,
                indicatorRows,
                mock(StrategyTemplateRepository.class),
                mock(IndicatorRepository.class),
                mock(StrategySubscriptionRepository.class),
                mock(TradingAccountRepository.class),
                mock(UserBrokerRepository.class),
                mock(SharedStrategyConfigService.class),
                mock(SubscriptionFanOut.class),
                mock(IndicatorParams.class),
                mock(UserStrategyValidator.class),
                mock(SymbolResolver.class),
                mock(RuleTrees.class),
                fixedParameters,
                new JsonSupport(new ObjectMapper()));
    }

    private static Indicator indicator(UUID id, String name, String schema) {
        Indicator indicator = new Indicator();
        indicator.setId(id);
        indicator.setName(name);
        indicator.setParamSchema(schema);
        return indicator;
    }

    private UserStrategyIndicator usage(Indicator indicator, String slot, int order,
                                        String params, boolean enabled) {
        UserStrategyIndicator row = new UserStrategyIndicator();
        row.setId(UUID.randomUUID());
        row.setUserStrategy(strategy);
        row.setIndicator(indicator);
        row.setSlot(slot);
        row.setDisplayOrder(order);
        row.setParams(params);
        row.setEnabled(enabled);
        return row;
    }

    private void withRows(UserStrategyIndicator... rows) {
        when(indicatorRows.findByUserStrategy_IdOrderByDisplayOrderAsc(STRATEGY_ID))
                .thenReturn(List.of(rows));
    }

    private UserStrategyResponse read() {
        return service.get(EMAIL, STRATEGY_ID);
    }

    /** The case the grouping exists for: one template, the same indicator twice. */
    @Test
    void twoUsagesOfOneIndicatorLandInOneGroup() {
        Indicator ema = indicator(EMA_ID, "EMA", EMA_SCHEMA);
        Indicator rsi = indicator(RSI_ID, "RSI", """
                {"period":{"type":"int","default":14}}""");
        withRows(
                usage(ema, "fast", 0, """
                        {"period":9}""", true),
                usage(ema, "slow", 1, """
                        {"period":21}""", true),
                usage(rsi, null, 2, """
                        {"period":14}""", true));

        List<UserStrategyIndicatorGroupResponse> groups = read().indicatorGroups();

        assertEquals(2, groups.size(), "one group per indicator name, not per usage");
        assertEquals("EMA", groups.get(0).indicatorName());
        assertEquals(EMA_ID, groups.get(0).indicatorId());
        assertEquals(2, groups.get(0).count());
        assertEquals(List.of("fast", "slow"),
                groups.get(0).indicators().stream()
                        .map(UserStrategyIndicatorResponse::slot).toList(),
                "the usages keep the flat list's displayOrder");
        assertEquals(List.of(Map.of("period", 9), Map.of("period", 21)),
                groups.get(0).indicators().stream()
                        .map(UserStrategyIndicatorResponse::params).toList(),
                "and each keeps its own tuning");
        assertEquals("RSI", groups.get(1).indicatorName());
        assertEquals(1, groups.get(1).count());
    }

    /** Grouping rearranges rows; it must not add, drop or alter one. */
    @Test
    void theGroupedShapeHoldsExactlyTheRowsTheFlatListDoes() {
        Indicator ema = indicator(EMA_ID, "EMA", EMA_SCHEMA);
        Indicator rsi = indicator(RSI_ID, "RSI", EMA_SCHEMA);
        withRows(
                usage(ema, "fast", 0, "{}", true),
                usage(rsi, null, 1, "{}", true),
                usage(ema, "slow", 2, "{}", true));

        UserStrategyResponse response = read();
        List<UserStrategyIndicatorResponse> flattened = response.indicatorGroups().stream()
                .flatMap(group -> group.indicators().stream())
                .toList();

        assertEquals(3, response.indicators().size());
        assertEquals(response.indicators().size(), flattened.size());
        assertTrue(flattened.containsAll(response.indicators()),
                "the same rows, only rearranged");
    }

    /**
     * The schema belongs to the indicator, so hoisting it to the group must not
     * change it - and a client that ignores the grouping still finds it on the row.
     */
    @Test
    void theSchemaIsHoistedToTheGroupAndKeptOnEachUsage() {
        Indicator ema = indicator(EMA_ID, "EMA", EMA_SCHEMA);
        withRows(usage(ema, "fast", 0, "{}", true), usage(ema, "slow", 1, "{}", true));

        UserStrategyIndicatorGroupResponse group = read().indicatorGroups().get(0);

        assertFalse(group.schema().isEmpty());
        group.indicators().forEach(usage ->
                assertEquals(group.schema(), usage.schema(),
                        "the group's schema is the usage's schema"));
    }

    /**
     * A section header renders one enabled flag for the whole group, so it has to
     * mean "any usage is live" - a group shown as off while it still trades would
     * be the wrong way round.
     */
    @Test
    void aGroupIsEnabledWhileAnyOfItsUsagesIs() {
        Indicator ema = indicator(EMA_ID, "EMA", EMA_SCHEMA);
        Indicator rsi = indicator(RSI_ID, "RSI", EMA_SCHEMA);
        withRows(
                usage(ema, "fast", 0, "{}", false),
                usage(ema, "slow", 1, "{}", true),
                usage(rsi, null, 2, "{}", false));

        List<UserStrategyIndicatorGroupResponse> groups = read().indicatorGroups();

        assertTrue(groups.get(0).enabled(), "one live usage keeps the group live");
        assertFalse(groups.get(1).enabled(), "all off means off");
    }

    @Test
    void aStrategyWithNoIndicatorsGetsNoGroups() {
        withRows();

        assertTrue(read().indicatorGroups().isEmpty(),
                "an empty array, not a group with nothing in it");
    }

    // ------------------------------------------------- the template's own count

    /**
     * A template's tree resolves to distinct NAMES, so the number that tells a
     * builder form how many tuning rows to draw is how many nodes named each one.
     */
    @Test
    void theRuleTreeCountsUsagesPerIndicatorName() {
        JsonSupport json = new JsonSupport(new ObjectMapper());

        Map<String, Integer> counts = IndicatorResolver.indicatorNameCounts(json.readTree("""
                {"entry": {"and": [
                    {"ind": "EMA", "params": {"period": "$fast"}},
                    {"ind": "EMA", "params": {"period": "$slow"}},
                    {"ind": "RSI", "params": {"period": "$rsi"}}]}}"""));

        assertEquals(Map.of("EMA", 2, "RSI", 1), counts);
        assertEquals(List.of("EMA", "RSI"), List.copyOf(counts.keySet()),
                "in the order the tree names them");
    }

    /** The two readings of the same tree cannot disagree about which names are in it. */
    @Test
    void theCountsCoverExactlyTheNamesTheResolverReports() {
        JsonSupport json = new JsonSupport(new ObjectMapper());
        var tree = json.readTree("""
                {"entry": {"ind": "EMA AVERAGING", "params": {"k": "$k", "d": "$d"}},
                 "exit":  {"ind": "RSI", "params": {"period": 14}}}""");

        assertEquals(IndicatorResolver.indicatorNames(tree),
                IndicatorResolver.indicatorNameCounts(tree).keySet());
    }

    @Test
    void anEmptyTreeCountsNothing() {
        assertTrue(IndicatorResolver.indicatorNameCounts(null).isEmpty());
    }
}

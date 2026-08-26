package com.example.tradeLedger;

import com.example.tradeLedger.dto.UserStrategyIndicatorResponse;
import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.entity.User;
import com.example.tradeLedger.entity.UserStrategy;
import com.example.tradeLedger.entity.UserStrategyIndicator;
import com.example.tradeLedger.indicator.IndicatorResolver;
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

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A strategy's indicator tuning as the response carries it: one flat row per
 * usage, in display order.
 *
 * The case worth pinning is a template naming the same indicator more than once -
 * two EMAs on different periods, an RSI filter per side. Those rows look alike,
 * so what has to hold is that each keeps its OWN slot and values, since the slot
 * is the only thing telling them apart on the way back in.
 *
 * A row carries values and nothing else. The schema they are validated against
 * belongs to the indicator and is read from the template; the row id, the
 * indicator id, displayOrder and enabled were dropped along with the
 * grouped-by-name arrangement that used to sit beside this list, none of which
 * anything read.
 */
class IndicatorUsageTest {

    private static final String EMAIL = "trader@example.com";
    private static final UUID USER_ID = UUID.fromString("00000000-1111-4222-8333-444444444444");
    private static final UUID STRATEGY_ID = UUID.fromString("11111111-2222-4333-8444-555555555555");

    private static final UUID EMA_ID = UUID.fromString("b2e4a1c8-1f3d-4e6a-9b2c-5d7e8f0a1b2c");
    private static final UUID RSI_ID = UUID.fromString("c3f5b2d9-2e4c-4f7b-8a3d-6e8f9a0b1c2d");

    private static final String EMA_SCHEMA = """
            {"period":{"type":"int","min":1,"max":300,"default":21}}""";

    private UserStrategyIndicatorRepository indicatorRows;
    private UserStrategyServiceImpl service;
    private UserStrategy strategy;

    @BeforeEach
    void setUp() {
        User user = new User();
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

        UserStrategyRepository strategies = mock(UserStrategyRepository.class);
        when(strategies.findByIdAndUser_Id(STRATEGY_ID, USER_ID))
                .thenReturn(java.util.Optional.of(strategy));

        indicatorRows = mock(UserStrategyIndicatorRepository.class);
        SharedStrategyConfigRepository sharedConfigs = mock(SharedStrategyConfigRepository.class);
        when(sharedConfigs.countByStrategyIds(any())).thenReturn(List.of());

        service = new UserStrategyServiceImpl(
                currentUser,
                strategies,
                indicatorRows,
                mock(StrategyTemplateRepository.class),
                mock(IndicatorRepository.class),
                mock(StrategySubscriptionRepository.class),
                sharedConfigs,
                mock(TradingAccountRepository.class),
                mock(UserBrokerRepository.class),
                mock(SharedStrategyConfigService.class),
                mock(SubscriptionFanOut.class),
                mock(IndicatorParams.class),
                mock(UserStrategyValidator.class),
                mock(SymbolResolver.class),
                mock(RuleTrees.class),
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

    /** The batched finder is what a response reads through, however many strategies it covers. */
    private void withRows(UserStrategyIndicator... rows) {
        when(indicatorRows.findByUserStrategy_IdInOrderByUserStrategy_IdAscDisplayOrderAsc(any()))
                .thenReturn(List.of(rows));
    }

    private List<UserStrategyIndicatorResponse> read() {
        return service.get(EMAIL, STRATEGY_ID).indicators();
    }

    /** The case the slot exists for: one template, the same indicator twice. */
    @Test
    void twoUsagesOfOneIndicatorStayTwoRowsWithTheirOwnTuning() {
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

        List<UserStrategyIndicatorResponse> indicators = read();

        assertEquals(3, indicators.size(), "one row per usage, not per indicator name");
        assertEquals(List.of("EMA", "EMA", "RSI"),
                indicators.stream().map(UserStrategyIndicatorResponse::indicatorName).toList());
        assertEquals(List.of("fast", "slow"),
                indicators.stream()
                        .filter(row -> "EMA".equals(row.indicatorName()))
                        .map(UserStrategyIndicatorResponse::slot).toList(),
                "the repeats are told apart by slot, in displayOrder");
        assertEquals(List.of(Map.of("period", 9), Map.of("period", 21), Map.of("period", 14)),
                indicators.stream().map(UserStrategyIndicatorResponse::params).toList(),
                "and each keeps its own tuning");
    }

    /**
     * The row carries what a caller sends back and nothing else.
     *
     * Its schema is the INDICATOR's, the same for every strategy using it, and is
     * read from the template; its row id and displayOrder were addressing and
     * ordering a caller does not need, since the array is already in order and a
     * write addresses by name and slot.
     */
    @Test
    void aUsageCarriesItsValuesAndNothingElse() {
        Indicator ema = indicator(EMA_ID, "EMA", EMA_SCHEMA);
        withRows(usage(ema, "fast", 0, """
                {"period":9}""", true));

        assertEquals(List.of("indicatorName", "slot", "params"),
                Arrays.stream(UserStrategyIndicatorResponse.class.getRecordComponents())
                        .map(RecordComponent::getName).toList());
        assertEquals(Map.of("period", 9), read().get(0).params());
    }

    @Test
    void aStrategyWithNoIndicatorsGetsAnEmptyArray() {
        withRows();

        assertTrue(read().isEmpty(), "an empty array, not null");
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
                {"entry": {"ind": "EMA Averaging", "params": {"k": "$k", "d": "$d"}},
                 "exit":  {"ind": "RSI", "params": {"period": 14}}}""");

        assertEquals(IndicatorResolver.indicatorNames(tree),
                IndicatorResolver.indicatorNameCounts(tree).keySet());
    }

    @Test
    void anEmptyTreeCountsNothing() {
        assertTrue(IndicatorResolver.indicatorNameCounts(null).isEmpty());
    }
}

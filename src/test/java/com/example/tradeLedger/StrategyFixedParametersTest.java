package com.example.tradeLedger;

import com.example.tradeLedger.dto.FixedParameterGroupResponse;
import com.example.tradeLedger.dto.FixedParameterResponse;
import com.example.tradeLedger.dto.UserStrategyResponse;
import com.example.tradeLedger.entity.Exchange;
import com.example.tradeLedger.entity.FixedParameter;
import com.example.tradeLedger.entity.Symbol;
import com.example.tradeLedger.repository.FixedParameterRepository;
import com.example.tradeLedger.repository.ExchangeRepository;
import com.example.tradeLedger.repository.SymbolRepository;
import com.example.tradeLedger.serviceImpl.FixedParameterOptions;
import com.example.tradeLedger.serviceImpl.StrategyFixedParameters;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The fixed knobs a strategy can carry, arranged as the sections a form draws.
 *
 * Two things are worth pinning, because neither is recomputable from either side
 * alone. The first is WHICH descriptors take part: the deployment ones describe
 * subscription columns, so a strategy form offering them would be promising
 * settings the row has nowhere to keep. The second is the OPTIONS - the symbol
 * and exchange knobs have no fixed choice list, they read one from the tables, and
 * a form that got an empty select could not pick a market at all.
 *
 * Values are not part of this shape. They are flat fields on the strategy
 * response, which a form binds to by descriptor name - the last test here is what
 * keeps those two lists from drifting apart.
 */
class StrategyFixedParametersTest {

    private FixedParameterRepository catalog;
    private SymbolRepository symbols;
    private ExchangeRepository exchanges;
    private StrategyFixedParameters fixedParameters;

    @BeforeEach
    void setUp() {
        catalog = mock(FixedParameterRepository.class);
        symbols = mock(SymbolRepository.class);
        when(symbols.findByActiveTrueOrderBySymbolAsc()).thenReturn(List.of(
                symbol("BANKNIFTY"), symbol("NIFTY")));
        exchanges = mock(ExchangeRepository.class);
        when(exchanges.findByStatusOrderByNameAsc(Exchange.STATUS_ACTIVE))
                .thenReturn(List.of(exchange("BSE"), exchange("NSE")));
        fixedParameters = new StrategyFixedParameters(catalog,
                new FixedParameterOptions(symbols, exchanges, new JsonSupport(new ObjectMapper())));
    }

    private static Exchange exchange(String code) {
        Exchange exchange = new Exchange();
        exchange.setId(UUID.randomUUID());
        exchange.setCode(code);
        exchange.setName(code + " exchange");
        exchange.setStatus(Exchange.STATUS_ACTIVE);
        return exchange;
    }

    private static Symbol symbol(String ticker) {
        return symbol(ticker, "NSE");
    }

    private static Symbol symbol(String ticker, String exchangeCode) {
        Symbol symbol = new Symbol();
        symbol.setId(UUID.randomUUID());
        symbol.setSymbol(ticker);
        symbol.setActive(true);
        symbol.setExchange(exchange(exchangeCode));
        return symbol;
    }

    /** The catalog as the seeder leaves it, in the order the repository returns it. */
    private void seedCatalog() {
        when(catalog.findByActiveOrderByParamGroupAscDisplayOrderAscNameAsc(true)).thenReturn(List.of(
                descriptor("Exits", 1, "slPct", "decimal", """
                        {"min":0,"max":100}"""),
                descriptor("Exits", 2, "tpPct", "decimal", null),
                descriptor("Instrument", 1, "derivative", "enum", null),
                descriptor("Instrument", 2, "ceEnabled", "bool", null),
                descriptor("Instrument", 3, "ceMoneyness", "enum", null),
                descriptor("Instrument", 4, "ceStrikeOffset", "int", null),
                descriptor("Market", 0, "exchangeCode", FixedParameter.TYPE_EXCHANGE, null),
                descriptor("Market", 1, "symbol", FixedParameter.TYPE_SYMBOL, null),
                descriptor("Market", 2, "candleDuration", "timeframe", null),
                descriptor("Market", 3, "triggerDuration", "timeframe", null),
                descriptor("Sizing", 1, "lotRule", "enum", null),
                descriptor("Sizing", 2, "baseLot", "int", null),
                // Describes a user_strategy_subscriptions column, not a strategy one.
                descriptor("Deployment", 4, "tradeMode", "enum", null)));
    }

    private static FixedParameter descriptor(String group, int order, String name,
                                             String dataType, String validation) {
        FixedParameter parameter = new FixedParameter();
        parameter.setId(UUID.randomUUID());
        parameter.setName(name);
        parameter.setLabel(name + " label");
        parameter.setDataType(dataType);
        parameter.setScope(FixedParameter.SCOPE_EXECUTION);
        parameter.setValidation(validation);
        parameter.setParamGroup(group);
        parameter.setDisplayOrder(order);
        parameter.setActive(true);
        return parameter;
    }

    private static Map<String, FixedParameterResponse> byName(
            List<FixedParameterGroupResponse> groups) {
        return groups.stream()
                .flatMap(group -> group.parameters().stream())
                .collect(Collectors.toMap(FixedParameterResponse::name, Function.identity()));
    }

    @Test
    void groupsTheKnobsByParamGroupInCatalogOrder() {
        seedCatalog();

        List<FixedParameterGroupResponse> groups = fixedParameters.descriptors();

        assertEquals(List.of("Exits", "Instrument", "Market", "Sizing"),
                groups.stream().map(FixedParameterGroupResponse::paramGroup).toList(),
                "the sections come back in the order the catalog is read in");
        assertEquals(List.of("slPct", "tpPct"),
                groups.get(0).parameters().stream().map(FixedParameterResponse::name).toList(),
                "and the rows inside keep their displayOrder");
        groups.forEach(group -> assertEquals(group.count(), group.parameters().size(),
                "the count and the rows must agree"));
    }

    /**
     * The deployment knobs describe subscription columns. A strategy has no such
     * column behind them, so offering one would be inventing a setting.
     */
    @Test
    void theDeploymentGroupIsNotAStrategySection() {
        seedCatalog();

        List<FixedParameterGroupResponse> groups = fixedParameters.descriptors();

        assertTrue(groups.stream().noneMatch(group -> "Deployment".equals(group.paramGroup())),
                "a strategy does not carry the deployment knobs");
        assertTrue(byName(groups).keySet().stream().allMatch(StrategyFixedParameters.knobNames()::contains),
                "only knobs this class recognizes appear");
    }

    // ------------------------------------------------------- the symbol knob

    /**
     * The one knob whose choices are rows. Nothing is stored for it, so if the read
     * path did not fill the list the form would render an empty select and the user
     * could not pick a market at all.
     */
    @Test
    void theSymbolKnobIsOfferedTheActiveSymbolsFromTheTable() {
        seedCatalog();

        FixedParameterResponse knob = byName(fixedParameters.descriptors()).get("symbol");

        assertEquals(FixedParameter.TYPE_SYMBOL, knob.dataType());
        assertEquals(List.of("BANKNIFTY", "NIFTY"), knob.validation().get("options"),
                "the tickers, in the order the table returns them");
        assertEquals("/api/v1/symbols", knob.validation().get("optionsSource"));
    }

    /**
     * The venue knob, on the same mechanism. It is the one that makes the symbol
     * knob's output submittable: a ticker is unique per exchange, so a form has to
     * be able to offer the venue beside it.
     */
    @Test
    void theExchangeKnobIsOfferedTheActiveExchangesFromTheTable() {
        seedCatalog();

        FixedParameterResponse knob = byName(fixedParameters.descriptors()).get("exchangeCode");

        assertEquals(FixedParameter.TYPE_EXCHANGE, knob.dataType());
        assertEquals(List.of("BSE", "NSE"), knob.validation().get("options"),
                "the codes, in the order the table returns them");
        assertEquals("/api/v1/exchanges", knob.validation().get("optionsSource"));
    }

    /** A disabled venue cannot carry a saveable symbol, so it is not offered. */
    @Test
    void onlyActiveExchangesAreOffered() {
        seedCatalog();
        when(exchanges.findByStatusOrderByNameAsc(Exchange.STATUS_ACTIVE))
                .thenReturn(List.of(exchange("NSE")));

        assertEquals(List.of("NSE"),
                byName(fixedParameters.descriptors()).get("exchangeCode").validation().get("options"));
    }

    /** The venue narrows the instrument, so a form has to draw it first. */
    @Test
    void theVenueIsOrderedBeforeTheInstrument() {
        seedCatalog();

        List<String> market = fixedParameters.descriptors().stream()
                .filter(group -> "Market".equals(group.paramGroup()))
                .flatMap(group -> group.parameters().stream())
                .map(FixedParameterResponse::name)
                .toList();

        assertEquals(List.of("exchangeCode", "symbol", "candleDuration", "triggerDuration"), market);
    }

    /** Listing an instrument has to change the list, which a stored copy could not do. */
    @Test
    void theSymbolOptionsFollowTheTable() {
        seedCatalog();
        when(symbols.findByActiveTrueOrderBySymbolAsc()).thenReturn(List.of(
                symbol("BANKNIFTY"), symbol("FINNIFTY"), symbol("NIFTY")));

        assertEquals(List.of("BANKNIFTY", "FINNIFTY", "NIFTY"),
                byName(fixedParameters.descriptors()).get("symbol").validation().get("options"));
    }

    /**
     * A ticker is unique per exchange, not globally, so the same one may sit on two
     * venues - it is offered once and {@code exchangeCode} picks the venue. A
     * repeated entry would render as a duplicate line nobody could tell apart.
     */
    @Test
    void aTickerListedOnTwoExchangesIsOfferedOnce() {
        seedCatalog();
        when(symbols.findByActiveTrueOrderBySymbolAsc()).thenReturn(List.of(
                symbol("NIFTY"), symbol("NIFTY")));

        assertEquals(List.of("NIFTY"),
                byName(fixedParameters.descriptors()).get("symbol").validation().get("options"));
    }

    /** The bounds a form enforces come back as a map, not as the stored JSON string. */
    @Test
    void validationIsCarriedAsAMap() {
        seedCatalog();

        Map<String, FixedParameterResponse> knobs = byName(fixedParameters.descriptors());

        assertEquals(0, knobs.get("slPct").validation().get("min"));
        assertEquals(100, knobs.get("slPct").validation().get("max"));
        assertTrue(knobs.get("tpPct").validation().isEmpty(),
                "an unbounded knob gets an empty map, not a null");
    }

    /** Emptying the catalog costs the arrangement and nothing else. */
    @Test
    void anEmptyCatalogProducesNoGroupsRatherThanFailing() {
        when(catalog.findByActiveOrderByParamGroupAscDisplayOrderAscNameAsc(true))
                .thenReturn(List.of());

        assertTrue(fixedParameters.descriptors().isEmpty());
    }

    /**
     * The pairing nothing else enforces.
     *
     * A form binds a descriptor to a strategy field by string equality on
     * {@code name}, so a knob whose name matches no field on the response renders
     * an input that reads nothing and writes nowhere. The strategy response used to
     * carry the values itself, which made the mismatch impossible; now the two
     * shapes are independent, and this is what keeps them honest.
     */
    @Test
    void everyKnobNamesAFieldTheStrategyResponseCarries() {
        Set<String> fields = Arrays.stream(UserStrategyResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        List<String> orphans = StrategyFixedParameters.knobNames().stream()
                .filter(knob -> !fields.contains(knob))
                .sorted()
                .toList();

        assertTrue(orphans.isEmpty(),
                "knobs with no field on UserStrategyResponse to bind to: " + orphans);
    }
}

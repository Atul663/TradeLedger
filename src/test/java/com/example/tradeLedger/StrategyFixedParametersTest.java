package com.example.tradeLedger;

import com.example.tradeLedger.dto.FixedParameterGroupResponse;
import com.example.tradeLedger.dto.StrategyFixedParameterGroupResponse;
import com.example.tradeLedger.dto.StrategyFixedParameterResponse;
import com.example.tradeLedger.entity.Derivative;
import com.example.tradeLedger.entity.FixedParameter;
import com.example.tradeLedger.entity.LotRule;
import com.example.tradeLedger.entity.Moneyness;
import com.example.tradeLedger.entity.Symbol;
import com.example.tradeLedger.entity.UserStrategy;
import com.example.tradeLedger.repository.FixedParameterRepository;
import com.example.tradeLedger.repository.SymbolRepository;
import com.example.tradeLedger.serviceImpl.FixedParameterOptions;
import com.example.tradeLedger.serviceImpl.StrategyFixedParameters;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The fixed knobs arranged as a form: descriptors from the catalog, values from
 * the strategy's own columns, folded into sections.
 *
 * Two things are worth pinning, because neither is recomputable from either side
 * alone. The first is the JOIN - a descriptor called 'slPct' has to come back
 * carrying {@code user_strategies.sl_pct} and not some other column's number,
 * and nothing in the schema enforces that pairing. The second is which
 * descriptors take part at all: the deployment ones describe subscription
 * columns, so a strategy that reported them would be claiming settings it does
 * not have.
 */
class StrategyFixedParametersTest {

    private FixedParameterRepository catalog;
    private SymbolRepository symbols;
    private StrategyFixedParameters fixedParameters;

    @BeforeEach
    void setUp() {
        catalog = mock(FixedParameterRepository.class);
        symbols = mock(SymbolRepository.class);
        when(symbols.findByActiveTrueOrderBySymbolAsc()).thenReturn(List.of(
                symbol("BANKNIFTY"), symbol("NIFTY")));
        fixedParameters = new StrategyFixedParameters(catalog,
                new FixedParameterOptions(symbols, new JsonSupport(new ObjectMapper())));
    }

    private static Symbol symbol(String ticker) {
        Symbol symbol = new Symbol();
        symbol.setId(UUID.randomUUID());
        symbol.setSymbol(ticker);
        symbol.setActive(true);
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
                descriptor("Market", 0, "symbol", FixedParameter.TYPE_SYMBOL, null),
                descriptor("Market", 1, "candleDuration", "timeframe", null),
                descriptor("Market", 2, "triggerDuration", "timeframe", null),
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

    private static UserStrategy strategy() {
        UserStrategy strategy = new UserStrategy();
        strategy.setSymbol(symbol("NIFTY"));
        strategy.setCandleDuration("5m");
        strategy.setTriggerDuration("1m");
        strategy.setDerivative(Derivative.OPTION);
        strategy.setCeEnabled(true);
        strategy.setCeMoneyness(Moneyness.OTM);
        strategy.setCeStrikeOffset(3);
        strategy.setLotRule(LotRule.DOUBLE);
        strategy.setBaseLot(65);
        strategy.setSlPct(new BigDecimal("1.50"));
        return strategy;
    }

    private static Map<String, StrategyFixedParameterResponse> byName(
            List<StrategyFixedParameterGroupResponse> groups) {
        return groups.stream()
                .flatMap(group -> group.parameters().stream())
                .collect(java.util.stream.Collectors.toMap(
                        StrategyFixedParameterResponse::name, Function.identity()));
    }

    @Test
    void groupsTheKnobsByParamGroupInCatalogOrder() {
        seedCatalog();

        List<StrategyFixedParameterGroupResponse> groups = fixedParameters.forStrategy(strategy());

        assertEquals(List.of("Exits", "Instrument", "Market", "Sizing"),
                groups.stream().map(StrategyFixedParameterGroupResponse::paramGroup).toList(),
                "the sections come back in the order the catalog is read in");
        assertEquals(List.of("slPct", "tpPct"),
                groups.get(0).parameters().stream()
                        .map(StrategyFixedParameterResponse::name).toList(),
                "and the rows inside keep their displayOrder");
        groups.forEach(group -> assertEquals(group.count(), group.parameters().size(),
                "the count and the rows must agree"));
    }

    /**
     * The join by name is the whole point of the shape, and nothing in the schema
     * enforces it - a descriptor wired to the wrong getter would render a form
     * that silently shows one field's value under another's label.
     */
    @Test
    void eachDescriptorCarriesTheValueOfTheColumnItNames() {
        seedCatalog();

        Map<String, StrategyFixedParameterResponse> knobs = byName(
                fixedParameters.forStrategy(strategy()));

        assertEquals("5m", knobs.get("candleDuration").value());
        assertEquals("1m", knobs.get("triggerDuration").value());
        assertEquals("OPTION", knobs.get("derivative").value());
        assertEquals(true, knobs.get("ceEnabled").value());
        assertEquals("OTM", knobs.get("ceMoneyness").value());
        assertEquals(3, knobs.get("ceStrikeOffset").value());
        assertEquals("DOUBLE", knobs.get("lotRule").value());
        assertEquals(65, knobs.get("baseLot").value());
        assertEquals(new BigDecimal("1.50"), knobs.get("slPct").value());
    }

    /** An unset column is null, not the descriptor's suggested default. */
    @Test
    void anUnsetColumnComesBackNull() {
        seedCatalog();

        Map<String, StrategyFixedParameterResponse> knobs = byName(
                fixedParameters.forStrategy(strategy()));

        assertNull(knobs.get("tpPct").value(), "the strategy carries no take profit");
        assertNotNull(knobs.get("slPct").value(), "but it does carry a stop");
    }

    /**
     * The deployment knobs describe subscription columns. A strategy has no such
     * column to read, so reporting one would be inventing a setting.
     */
    @Test
    void theDeploymentGroupIsNotAStrategySection() {
        seedCatalog();

        List<StrategyFixedParameterGroupResponse> groups = fixedParameters.forStrategy(strategy());

        assertTrue(groups.stream().noneMatch(group -> "Deployment".equals(group.paramGroup())),
                "a strategy does not carry the deployment knobs");
        assertTrue(byName(groups).keySet().stream().allMatch(StrategyFixedParameters.knobNames()::contains),
                "only knobs this class knows how to read appear");
    }

    // ------------------------------------------------------- the symbol knob

    /**
     * The one knob whose choices are rows. Nothing is stored for it, so if the
     * read path did not fill the list the form would render an empty select and
     * the user could not pick a market at all.
     */
    @Test
    void theSymbolKnobIsOfferedTheActiveSymbolsFromTheTable() {
        seedCatalog();

        StrategyFixedParameterResponse knob = byName(fixedParameters.forStrategy(strategy()))
                .get("symbol");

        assertEquals(FixedParameter.TYPE_SYMBOL, knob.dataType());
        assertEquals(List.of("BANKNIFTY", "NIFTY"), knob.validation().get("options"),
                "the tickers, in the order the table returns them");
        assertEquals("/api/v1/symbols", knob.validation().get("optionsSource"));
        assertEquals("NIFTY", knob.value(), "the ticker, matching the vocabulary of its options");
    }

    /** Listing an instrument has to change the list, which a stored copy could not do. */
    @Test
    void theSymbolOptionsFollowTheTable() {
        seedCatalog();
        when(symbols.findByActiveTrueOrderBySymbolAsc()).thenReturn(List.of(
                symbol("BANKNIFTY"), symbol("FINNIFTY"), symbol("NIFTY")));

        assertEquals(List.of("BANKNIFTY", "FINNIFTY", "NIFTY"),
                byName(fixedParameters.forStrategy(strategy())).get("symbol").validation()
                        .get("options"));
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
                byName(fixedParameters.forStrategy(strategy())).get("symbol").validation()
                        .get("options"));
    }

    /** A strategy saved on template defaults has no market yet - that is not an error. */
    @Test
    void aStrategyWithNoMarketReportsANullSymbolAndStillGetsTheOptions() {
        seedCatalog();
        UserStrategy blank = strategy();
        blank.setSymbol(null);

        StrategyFixedParameterResponse knob = byName(fixedParameters.forStrategy(blank))
                .get("symbol");

        assertNull(knob.value());
        assertEquals(List.of("BANKNIFTY", "NIFTY"), knob.validation().get("options"),
                "there is still a list to pick from");
    }

    /** The bounds a form enforces come back as a map, not as the stored JSON string. */
    @Test
    void validationIsCarriedAsAMap() {
        seedCatalog();

        StrategyFixedParameterResponse slPct = byName(fixedParameters.forStrategy(strategy()))
                .get("slPct");

        assertEquals(0, slPct.validation().get("min"));
        assertEquals(100, slPct.validation().get("max"));
        assertTrue(byName(fixedParameters.forStrategy(strategy())).get("tpPct").validation().isEmpty(),
                "an unbounded knob gets an empty map, not a null");
    }

    /**
     * A template has no values, so it gets the same sections empty-handed - and it
     * has to be the SAME sections, or a form drawing a blank template and the same
     * form reopened on a saved strategy would disagree about which fields exist.
     */
    @Test
    void theDescriptorOnlyShapeHoldsTheSameSectionsAndFields() {
        seedCatalog();

        List<FixedParameterGroupResponse> descriptors = fixedParameters.descriptors();
        List<StrategyFixedParameterGroupResponse> withValues = fixedParameters.forStrategy(strategy());

        assertEquals(
                withValues.stream().map(StrategyFixedParameterGroupResponse::paramGroup).toList(),
                descriptors.stream().map(FixedParameterGroupResponse::paramGroup).toList());
        assertEquals(
                withValues.stream().flatMap(g -> g.parameters().stream())
                        .map(StrategyFixedParameterResponse::name).toList(),
                descriptors.stream().flatMap(g -> g.parameters().stream())
                        .map(com.example.tradeLedger.dto.FixedParameterResponse::name).toList());
    }

    /** Emptying the catalog costs the arrangement and nothing else. */
    @Test
    void anEmptyCatalogProducesNoGroupsRatherThanFailing() {
        when(catalog.findByActiveOrderByParamGroupAscDisplayOrderAscNameAsc(true))
                .thenReturn(List.of());

        assertTrue(fixedParameters.forStrategy(strategy()).isEmpty());
        assertTrue(fixedParameters.descriptors().isEmpty());
    }
}

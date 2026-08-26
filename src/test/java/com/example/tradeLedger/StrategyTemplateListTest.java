package com.example.tradeLedger;

import com.example.tradeLedger.dto.StrategyTemplateDetailResponse;
import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.repository.IndicatorRepository;
import com.example.tradeLedger.repository.SharedStrategyConfigRepository;
import com.example.tradeLedger.repository.StrategyTemplateRepository;
import com.example.tradeLedger.repository.UserStrategyRepository;
import com.example.tradeLedger.serviceImpl.StrategyTemplateServiceImpl;
import com.example.tradeLedger.serviceImpl.StrategyTemplateValidator;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the template list costs, and what it no longer carries.
 *
 * The endpoint took eleven seconds because every field of every row was fetched
 * per row: an indicator lookup per name the tree held, two counts per template,
 * and - worst - the whole fixed-parameter catalogue rebuilt per template, which
 * re-read the entire symbols table each time to fill the instrument select.
 *
 * The count assertions below are the point of this class. A response shaped by a
 * per-row mapper invites exactly that regression back, and it is invisible in a
 * unit test that only looks at the output.
 */
class StrategyTemplateListTest {

    private StrategyTemplateRepository templates;
    private IndicatorRepository indicators;
    private SharedStrategyConfigRepository configs;
    private UserStrategyRepository strategies;
    private StrategyTemplateServiceImpl service;

    private static StrategyTemplate template(String name, String tree) {
        StrategyTemplate template = new StrategyTemplate();
        template.setId(UUID.randomUUID());
        template.setName(name);
        template.setRuleTree(tree);
        template.setActive(true);
        return template;
    }

    private static Indicator indicator(String name) {
        Indicator indicator = new Indicator();
        indicator.setId(UUID.randomUUID());
        indicator.setName(name);
        indicator.setParamSchema("{\"k\":{\"type\":\"int\",\"default\":9,\"label\":\"Short (k)\"}}");
        indicator.setActive(true);
        return indicator;
    }

    @BeforeEach
    void setUp() {
        templates = mock(StrategyTemplateRepository.class);
        indicators = mock(IndicatorRepository.class);
        configs = mock(SharedStrategyConfigRepository.class);
        strategies = mock(UserStrategyRepository.class);

        JsonSupport json = new JsonSupport(new ObjectMapper());
        service = new StrategyTemplateServiceImpl(templates, configs, strategies, indicators,
                new StrategyTemplateValidator(indicators, json), json);

        when(indicators.findAll()).thenReturn(List.of(
                indicator("EMA Crossover"), indicator("RSI Reversal"), indicator("MACD Momentum")));
        when(configs.countByStrategyIds(any())).thenReturn(List.of());
        when(strategies.countByStrategyIds(any())).thenReturn(List.of());
    }

    private List<StrategyTemplateDetailResponse> listOfThree() {
        when(templates.findByActiveOrderByNameAsc(true)).thenReturn(List.of(
                template("EMA Crossover", "{\"entry\":{\"ind\":\"EMA Crossover\",\"params\":{\"k\":\"$k\"}}}"),
                template("RSI Reversal", "{\"entry\":{\"ind\":\"RSI Reversal\",\"params\":{\"k\":\"$k\"}}}"),
                template("MACD Momentum", "{\"entry\":{\"ind\":\"MACD Momentum\",\"params\":{\"k\":\"$k\"}}}")));
        return service.list(true, null);
    }

    @Test
    void readsTheIndicatorCatalogueOnceForThePageRatherThanOncePerName() {
        assertEquals(3, listOfThree().size());

        verify(indicators, times(1)).findAll();
        verify(indicators, never()).findByNameIgnoreCase(anyString());
        verify(indicators, never()).findByName(anyString());
    }

    @Test
    void countsEveryTemplateInOneQueryEachRatherThanOnePerTemplate() {
        listOfThree();

        verify(configs, times(1)).countByStrategyIds(any());
        verify(strategies, times(1)).countByStrategyIds(any());
        verify(configs, never()).countByStrategy_Id(any());
        verify(strategies, never()).countByStrategy_Id(any());
    }

    /** An empty page asks nothing at all - there is nothing to ask about. */
    @Test
    void asksNothingWhenNoTemplateMatches() {
        when(templates.findByActiveOrderByNameAsc(true)).thenReturn(List.of());

        assertTrue(service.list(true, null).isEmpty());
        verify(indicators, never()).findAll();
        verify(configs, never()).countByStrategyIds(any());
    }

    @Test
    void resolvesEachTemplateAgainstTheRightIndicator() {
        List<StrategyTemplateDetailResponse> list = listOfThree();

        assertEquals(List.of("EMA Crossover", "RSI Reversal", "MACD Momentum"),
                list.stream().map(r -> r.indicators().get(0).name()).toList());
        list.forEach(r -> assertTrue(r.unknownIndicators().isEmpty(), r.name()));
    }

    /**
     * The tree is platform logic and the fixed knobs are identical on every
     * template - the client reads those once from /fixed-parameters. Both were
     * carried here and both were the cost.
     */
    @Test
    void carriesNeitherTheRuleTreeNorTheFixedKnobs() {
        List<String> components = Arrays.stream(StrategyTemplateDetailResponse.class.getRecordComponents())
                .map(RecordComponent::getName).toList();

        assertFalse(components.contains("ruleTree"), components.toString());
        assertFalse(components.contains("fixedParameters"), components.toString());
        assertTrue(components.contains("indicatorGroups"), components.toString());
    }
}

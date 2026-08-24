package com.example.tradeLedger;

import com.example.tradeLedger.dto.FixedParameterRequest;
import com.example.tradeLedger.dto.FixedParameterResponse;
import com.example.tradeLedger.entity.FixedParameter;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.FixedParameterRepository;
import com.example.tradeLedger.serviceImpl.FixedParameterServiceImpl;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The write rules for the fixed-knob descriptor catalog.
 *
 * A descriptor is only worth having if it is true: a form that pre-fills a
 * decimal field with "none", or offers a choice that is not one of the knob's
 * options, is worse than a hardcoded field. So the default is parsed against the
 * type and the bounds on every write, and these pin that down - along with the
 * partial-update rule that judges the RESULTING row rather than the fragment
 * that arrived.
 */
class FixedParameterWriteTest {

    private static final UUID ID = UUID.fromString("3f2a1b0c-9d8e-4f6a-8b5c-2d1e0f9a8b7c");

    private FixedParameterRepository repository;
    private FixedParameterServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(FixedParameterRepository.class);
        service = new FixedParameterServiceImpl(repository, new JsonSupport(new ObjectMapper()));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static FixedParameterRequest request(String name, String dataType, String defaultValue) {
        FixedParameterRequest request = new FixedParameterRequest();
        request.setName(name);
        request.setLabel("Stop loss %");
        request.setDataType(dataType);
        request.setDefaultValue(defaultValue);
        return request;
    }

    private static Map<String, Object> rules(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private static FixedParameter stored() {
        FixedParameter parameter = new FixedParameter();
        parameter.setId(ID);
        parameter.setName("slPct");
        parameter.setLabel("Stop loss %");
        parameter.setDataType(FixedParameter.TYPE_DECIMAL);
        parameter.setScope(FixedParameter.SCOPE_EXECUTION);
        parameter.setDefaultValue("2.5");
        parameter.setValidation("{\"min\":0,\"max\":100}");
        parameter.setParamGroup("exits");
        parameter.setDisplayOrder(1);
        return parameter;
    }

    private FixedParameter captureSaved() {
        ArgumentCaptor<FixedParameter> saved = ArgumentCaptor.forClass(FixedParameter.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    // ---------------------------------------------------------------- create

    @Test
    void createsADescriptorAndDefaultsItsScope() {
        FixedParameterRequest request = request("slPct", "DECIMAL", "2.5");
        request.setValidation(rules("min", 0, "max", 100));
        request.setParamGroup(" Exits ");

        FixedParameterResponse response = service.create(request);

        FixedParameter saved = captureSaved();
        assertEquals("slPct", saved.getName());
        assertEquals(FixedParameter.TYPE_DECIMAL, saved.getDataType(), "the type is lowercased");
        assertEquals(FixedParameter.SCOPE_EXECUTION, saved.getScope(),
                "a knob is personal unless it is declared to be part of the signal");
        assertEquals("exits", saved.getParamGroup(), "the group is normalized so the ordering groups");
        assertTrue(saved.isActive());
        assertEquals(Map.of("min", 0, "max", 100), response.validation(),
                "the bounds come back as a map, not a JSON string");
    }

    @Test
    void refusesADefaultThatIsNotOfItsType() {
        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> service.create(request("averagingCount", "int", "a few")));

        assertTrue(thrown.getMessage().contains("whole number"), thrown.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void refusesADefaultOutsideItsOwnBounds() {
        FixedParameterRequest request = request("slPct", "decimal", "150");
        request.setValidation(rules("min", 0, "max", 100));

        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> service.create(request));

        assertTrue(thrown.getMessage().contains("above validation.max"), thrown.getMessage());
    }

    @Test
    void anEnumNeedsItsOptionsAndADefaultDrawnFromThem() {
        FixedParameterRequest missingOptions = request("lotRule", "enum", "FIXED");
        assertTrue(assertThrows(StrategyValidationException.class,
                () -> service.create(missingOptions)).getMessage().contains("options is required"));

        FixedParameterRequest strayDefault = request("lotRule", "enum", "TRIPLE");
        strayDefault.setValidation(rules("options", List.of("FIXED", "DOUBLE", "CUMULATIVE")));
        assertTrue(assertThrows(StrategyValidationException.class,
                () -> service.create(strayDefault)).getMessage().contains("must be one of"));
    }

    @Test
    void boundsOnlyApplyToANumberAndOptionsOnlyToAnEnum() {
        FixedParameterRequest boundedText = request("note", "text", null);
        boundedText.setValidation(rules("min", 1));
        assertTrue(assertThrows(StrategyValidationException.class,
                () -> service.create(boundedText)).getMessage().contains("only apply to an int or decimal"));

        FixedParameterRequest optionedInt = request("baseLot", "int", "1");
        optionedInt.setValidation(rules("options", List.of(1, 2)));
        assertTrue(assertThrows(StrategyValidationException.class,
                () -> service.create(optionedInt)).getMessage().contains("only applies to an enum"));
    }

    /**
     * The knob it describes normalizes its timeframe through the same class, and
     * a descriptor that pre-filled '5M' would round-trip into a value the strategy
     * API then stored as '5m'.
     */
    @Test
    void aTimeframeDefaultIsNormalizedTheWayAStrategysIs() {
        service.create(request("candleDuration", "timeframe", " 5M "));

        assertEquals("5m", captureSaved().getDefaultValue());
    }

    @Test
    void refusesAMalformedTimeframeDefault() {
        assertTrue(assertThrows(StrategyValidationException.class,
                () -> service.create(request("candleDuration", "timeframe", "five minutes")))
                .getMessage().contains("defaultValue"));
    }

    @Test
    void refusesANameThatIsNotAMachineKey() {
        assertTrue(assertThrows(StrategyValidationException.class,
                () -> service.create(request("stop loss %", "decimal", null)))
                .getMessage().contains("name must start with a letter"));
    }

    @Test
    void refusesADuplicateNameWhateverItsCasing() {
        when(repository.existsByNameIgnoreCase("SLPCT")).thenReturn(true);

        assertThrows(ResourceConflictException.class,
                () -> service.create(request("SLPCT", "decimal", "2.5")));
        verify(repository, never()).save(any());
    }

    @Test
    void anUnboundedKnobStoresNoRulesAtAll() {
        service.create(request("note", "text", "hello"));

        assertNull(captureSaved().getValidation(), "an empty rule set is NULL, not {}");
    }

    // ---------------------------------------------------------------- update

    /**
     * The type, the default and the bounds are one decision. Retyping a knob while
     * its stored default no longer fits has to fail, even though the request
     * carries neither the default nor the options.
     */
    @Test
    void retypingIsJudgedAgainstTheResultingRowNotTheFragment() {
        when(repository.findById(ID)).thenReturn(Optional.of(stored()));
        FixedParameterRequest request = new FixedParameterRequest();
        request.setDataType("enum");

        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> service.update(ID, request));

        assertTrue(thrown.getMessage().contains("options is required"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("only apply to an int or decimal"),
                "the stored min/max no longer fit the new type either: " + thrown.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void retunesTheDefaultWithoutDisturbingTheRest() {
        when(repository.findById(ID)).thenReturn(Optional.of(stored()));
        FixedParameterRequest request = new FixedParameterRequest();
        request.setDefaultValue("3.0");

        service.update(ID, request);

        FixedParameter saved = captureSaved();
        assertEquals("3.0", saved.getDefaultValue());
        assertEquals("Stop loss %", saved.getLabel());
        assertEquals("exits", saved.getParamGroup());
        assertEquals(1, saved.getDisplayOrder());
    }

    @Test
    void anEmptyStringClearsTheDefault() {
        when(repository.findById(ID)).thenReturn(Optional.of(stored()));
        FixedParameterRequest request = new FixedParameterRequest();
        request.setDefaultValue("   ");

        service.update(ID, request);

        assertNull(captureSaved().getDefaultValue());
    }

    @Test
    void recasingTheNameIsNotACollisionWithItself() {
        when(repository.findById(ID)).thenReturn(Optional.of(stored()));
        when(repository.existsByNameIgnoreCase(anyString())).thenReturn(true);
        FixedParameterRequest request = new FixedParameterRequest();
        request.setName("SLPct");

        service.update(ID, request);

        assertEquals("SLPct", captureSaved().getName());
    }

    // ------------------------------------------------------------------ read

    @Test
    void listingFiltersByGroupAndScopeInFormOrder() {
        FixedParameter exit = stored();
        FixedParameter candle = new FixedParameter();
        candle.setName("candleDuration");
        candle.setLabel("Time frame");
        candle.setDataType(FixedParameter.TYPE_TIMEFRAME);
        candle.setScope(FixedParameter.SCOPE_SIGNAL);
        candle.setParamGroup("market");
        when(repository.findAllByOrderByParamGroupAscDisplayOrderAscNameAsc())
                .thenReturn(List.of(candle, exit));

        assertEquals(List.of("candleDuration"),
                service.list("MARKET", null, null).stream().map(FixedParameterResponse::name).toList());
        assertEquals(List.of("candleDuration"),
                service.list(null, "signal", null).stream().map(FixedParameterResponse::name).toList());
        assertEquals(List.of("candleDuration", "slPct"),
                service.list(null, null, null).stream().map(FixedParameterResponse::name).toList());
    }

    @Test
    void refusesAScopeFilterThatIsNotOne() {
        assertThrows(StrategyValidationException.class, () -> service.list(null, "hashed", null));
    }
}

package com.example.tradeLedger;

import com.example.tradeLedger.dto.FixedParameterGroupResponse;
import com.example.tradeLedger.dto.FixedParameterResponse;
import com.example.tradeLedger.entity.FixedParameter;
import com.example.tradeLedger.repository.FixedParameterRepository;
import com.example.tradeLedger.serviceImpl.FixedParameterServiceImpl;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The catalog folded into its sections.
 *
 * The grouping is the one thing a client cannot cheaply recompute - it would
 * otherwise have to know that the flat list arrives already sorted by group and
 * that consecutive rows sharing one are a section. So what these pin is that the
 * fold agrees with the list it folds: the same rows, the same order, and the same
 * answer to every filter.
 */
class FixedParameterGroupedReadTest {

    private FixedParameterRepository repository;
    private FixedParameterServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(FixedParameterRepository.class);
        service = new FixedParameterServiceImpl(repository, new JsonSupport(new ObjectMapper()));
    }

    private static FixedParameter row(String group, int order, String name, String scope) {
        FixedParameter parameter = new FixedParameter();
        parameter.setId(UUID.randomUUID());
        parameter.setName(name);
        parameter.setLabel(name);
        parameter.setDataType(FixedParameter.TYPE_TEXT);
        parameter.setScope(scope);
        parameter.setParamGroup(group);
        parameter.setDisplayOrder(order);
        parameter.setActive(true);
        return parameter;
    }

    /** The repository's own ordering: by group, then position, then name. */
    private void seed() {
        when(repository.findAllByOrderByParamGroupAscDisplayOrderAscNameAsc()).thenReturn(List.of(
                row("exits", 1, "slPct", FixedParameter.SCOPE_EXECUTION),
                row("exits", 2, "tpPct", FixedParameter.SCOPE_EXECUTION),
                row("market", 1, "candleDuration", FixedParameter.SCOPE_SIGNAL),
                row("market", 2, "triggerDuration", FixedParameter.SCOPE_EXECUTION),
                row("sizing", 1, "baseLot", FixedParameter.SCOPE_EXECUTION)));
    }

    private static List<String> names(FixedParameterGroupResponse group) {
        return group.parameters().stream().map(FixedParameterResponse::name).toList();
    }

    @Test
    void foldsTheListIntoOneGroupPerParamGroup() {
        seed();

        List<FixedParameterGroupResponse> groups = service.listGrouped(null, null, null);

        assertEquals(List.of("exits", "market", "sizing"),
                groups.stream().map(FixedParameterGroupResponse::paramGroup).toList());
        assertEquals(List.of("slPct", "tpPct"), names(groups.get(0)),
                "rows keep the flat list's order inside a group");
        groups.forEach(group -> assertEquals(group.count(), group.parameters().size(),
                "the count and the rows must agree"));
    }

    /** Two arrangements of one query: they cannot end up describing different catalogs. */
    @Test
    void theGroupedShapeHoldsExactlyTheRowsTheFlatListReturns() {
        seed();

        List<FixedParameterResponse> flat = service.list(null, null, null);
        List<FixedParameterResponse> grouped = service.listGrouped(null, null, null).stream()
                .flatMap(group -> group.parameters().stream())
                .toList();

        assertEquals(flat, grouped, "same rows, same order, only sectioned");
    }

    /** A group filter narrows it to that one group rather than changing the shape. */
    @Test
    void filteringByGroupReturnsThatOneGroup() {
        seed();

        List<FixedParameterGroupResponse> groups = service.listGrouped("MARKET", null, null);

        assertEquals(1, groups.size());
        assertEquals("market", groups.get(0).paramGroup());
        assertEquals(List.of("candleDuration", "triggerDuration"), names(groups.get(0)));
    }

    /**
     * The scope filter cuts across the sections, so a group has to report what
     * survived it - not how many knobs the section has.
     */
    @Test
    void theScopeFilterAppliesInsideTheGroupsAndTheCountFollows() {
        seed();

        List<FixedParameterGroupResponse> groups =
                service.listGrouped(null, FixedParameter.SCOPE_SIGNAL, null);

        assertEquals(1, groups.size(), "only the market section has a signal-scope knob");
        assertEquals(List.of("candleDuration"), names(groups.get(0)));
        assertEquals(1, groups.get(0).count());
    }

    /** A descriptor nobody assigned a section to still has to be reachable. */
    @Test
    void ungroupedDescriptorsCollectUnderANullGroup() {
        when(repository.findAllByOrderByParamGroupAscDisplayOrderAscNameAsc()).thenReturn(List.of(
                row(null, 1, "orphan", FixedParameter.SCOPE_EXECUTION),
                row("exits", 1, "slPct", FixedParameter.SCOPE_EXECUTION)));

        List<FixedParameterGroupResponse> groups = service.listGrouped(null, null, null);

        assertEquals(2, groups.size());
        assertTrue(groups.stream().anyMatch(group -> group.paramGroup() == null
                && names(group).equals(List.of("orphan"))));
    }

    @Test
    void anEmptyCatalogProducesNoGroups() {
        when(repository.findAllByOrderByParamGroupAscDisplayOrderAscNameAsc()).thenReturn(List.of());

        assertTrue(service.listGrouped(null, null, null).isEmpty());
    }
}

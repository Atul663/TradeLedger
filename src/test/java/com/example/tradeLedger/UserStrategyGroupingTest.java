package com.example.tradeLedger;

import com.example.tradeLedger.dto.UserStrategyGroupResponse;
import com.example.tradeLedger.dto.UserStrategyResponse;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.entity.User;
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
import com.example.tradeLedger.serviceImpl.StrategyFixedParameters;
import com.example.tradeLedger.serviceImpl.SubscriptionFanOut;
import com.example.tradeLedger.serviceImpl.SymbolResolver;
import com.example.tradeLedger.serviceImpl.UserStrategyServiceImpl;
import com.example.tradeLedger.serviceImpl.UserStrategyValidator;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The grouped read shape: the same rows the flat list returns, arranged one group
 * per template and tagged with that template's name.
 *
 * What is worth pinning is the arrangement, because it is the only thing a client
 * cannot recompute cheaply: which rows land in which group, the order the groups
 * come back in, and that the count agrees with what is actually inside. The row
 * contents are the flat list's contract and are covered where that is.
 */
class UserStrategyGroupingTest {

    private static final String EMAIL = "trader@example.com";

    private static final UUID USER_ID = UUID.fromString("00000000-1111-4222-8333-444444444444");
    private static final UUID AVERAGING_ID = UUID.fromString("3f1b0c7e-9a41-4c2e-9f11-2b7d5a6e8c01");
    private static final UUID CROSSOVER_ID = UUID.fromString("8c2d1e0f-7b36-4a15-9e42-0d3c6b5a4f92");

    private UserStrategyRepository strategies;
    private SharedStrategyConfigRepository sharedConfigs;
    private UserStrategyIndicatorRepository indicatorRows;
    private StrategySubscriptionRepository subscriptions;
    private UserStrategyServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);

        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.require(EMAIL)).thenReturn(user);

        strategies = mock(UserStrategyRepository.class);
        sharedConfigs = mock(SharedStrategyConfigRepository.class);
        when(sharedConfigs.countByStrategyIds(any())).thenReturn(List.of());
        indicatorRows = mock(UserStrategyIndicatorRepository.class);
        when(indicatorRows.findByUserStrategy_IdInOrderByUserStrategy_IdAscDisplayOrderAsc(any()))
                .thenReturn(List.of());
        subscriptions = mock(StrategySubscriptionRepository.class);
        when(subscriptions.countByUserStrategyIds(any())).thenReturn(List.of());
        service = new UserStrategyServiceImpl(
                currentUser,
                strategies,
                indicatorRows,
                mock(StrategyTemplateRepository.class),
                mock(IndicatorRepository.class),
                subscriptions,
                sharedConfigs,
                mock(TradingAccountRepository.class),
                mock(UserBrokerRepository.class),
                mock(SharedStrategyConfigService.class),
                mock(SubscriptionFanOut.class),
                mock(IndicatorParams.class),
                mock(UserStrategyValidator.class),
                mock(SymbolResolver.class),
                mock(RuleTrees.class),
                IndicatorGroupingTest.emptyFixedParameters(),
                new JsonSupport(new ObjectMapper()));
    }

    private static StrategyTemplate template(UUID id, String name) {
        return template(id, name, true);
    }

    private static StrategyTemplate template(UUID id, String name, boolean system) {
        StrategyTemplate template = new StrategyTemplate();
        template.setId(id);
        template.setName(name);
        template.setDescription(name + " logic");
        template.setSystem(system);
        return template;
    }

    private UserStrategy strategy(String name, StrategyTemplate template, boolean active) {
        UserStrategy strategy = new UserStrategy();
        strategy.setId(UUID.randomUUID());
        strategy.setUser(user);
        strategy.setStrategy(template);
        strategy.setName(name);
        strategy.setActive(active);
        return strategy;
    }

    private static List<String> names(UserStrategyGroupResponse group) {
        return group.strategies().stream().map(UserStrategyResponse::name).toList();
    }

    @Test
    void groupsByTemplateAndTagsEachGroupWithItsName() {
        StrategyTemplate averaging = template(AVERAGING_ID, "EMA Averaging");
        StrategyTemplate crossover = template(CROSSOVER_ID, "EMA Crossover");
        when(strategies.findByUser_IdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(
                strategy("NIFTY 21/9 both sides", averaging, true),
                strategy("NIFTY fast cross", crossover, true),
                strategy("BANKNIFTY 21/9", averaging, true)));

        List<UserStrategyGroupResponse> groups = service.listGrouped(EMAIL, null, null);

        assertEquals(2, groups.size(), "one group per template, not per strategy");

        UserStrategyGroupResponse first = groups.get(0);
        assertEquals(AVERAGING_ID, first.strategyId());
        assertEquals("EMA Averaging", first.strategyName(), "the group carries its tag");
        assertEquals("EMA Averaging logic", first.strategyDescription());
        assertEquals(2, first.count());
        assertEquals(List.of("NIFTY 21/9 both sides", "BANKNIFTY 21/9"), names(first),
                "rows keep the flat list's oldest-first order");

        assertEquals("EMA Crossover", groups.get(1).strategyName());
        assertEquals(List.of("NIFTY fast cross"), names(groups.get(1)));
    }

    /** A list screen reads its headings alphabetically, whatever order the rows arrived in. */
    @Test
    void groupsAreOrderedByName() {
        StrategyTemplate zulu = template(UUID.randomUUID(), "Zulu Breakout");
        StrategyTemplate alpha = template(UUID.randomUUID(), "alpha Reversal");
        StrategyTemplate mid = template(UUID.randomUUID(), "Momentum");
        when(strategies.findByUser_IdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(
                strategy("z", zulu, true), strategy("m", mid, true), strategy("a", alpha, true)));

        List<UserStrategyGroupResponse> groups = service.listGrouped(EMAIL, null, null);

        assertEquals(List.of("alpha Reversal", "Momentum", "Zulu Breakout"),
                groups.stream().map(UserStrategyGroupResponse::strategyName).toList(),
                "case-insensitively, so a lowercase name does not sort to the end");
    }

    /**
     * The count is what a heading renders, so it has to describe the rows actually
     * in the group - not how many the template has before filtering.
     */
    @Test
    void theActiveFilterAppliesInsideTheGroupsAndTheCountFollows() {
        StrategyTemplate averaging = template(AVERAGING_ID, "EMA Averaging");
        StrategyTemplate crossover = template(CROSSOVER_ID, "EMA Crossover");
        when(strategies.findByUser_IdAndActiveOrderByCreatedAtAsc(USER_ID, true)).thenReturn(List.of(
                strategy("live one", averaging, true),
                strategy("live two", averaging, true),
                strategy("live cross", crossover, true)));

        List<UserStrategyGroupResponse> groups = service.listGrouped(EMAIL, true, null);

        assertEquals(2, groups.get(0).count());
        assertEquals(groups.get(0).count(), groups.get(0).strategies().size(),
                "the count and the rows must agree");
        assertEquals(1, groups.get(1).count());
    }

    /**
     * The strategyId filter narrows to one group rather than changing the shape -
     * a client can hit the same endpoint either way.
     */
    @Test
    void filteringByTemplateReturnsThatOneGroup() {
        StrategyTemplate averaging = template(AVERAGING_ID, "EMA Averaging");
        when(strategies.findByUser_IdAndStrategy_IdOrderByCreatedAtAsc(USER_ID, AVERAGING_ID))
                .thenReturn(List.of(strategy("only one", averaging, true)));

        List<UserStrategyGroupResponse> groups = service.listGrouped(EMAIL, null, AVERAGING_ID);

        assertEquals(1, groups.size());
        assertEquals(AVERAGING_ID, groups.get(0).strategyId());
    }

    /** An archived row is dropped even when the query could not express both filters. */
    @Test
    void theArchiveFilterStillAppliesAlongsideTheTemplateFilter() {
        StrategyTemplate averaging = template(AVERAGING_ID, "EMA Averaging");
        when(strategies.findByUser_IdAndStrategy_IdOrderByCreatedAtAsc(USER_ID, AVERAGING_ID))
                .thenReturn(List.of(
                        strategy("live", averaging, true),
                        strategy("archived", averaging, false)));

        List<UserStrategyGroupResponse> groups = service.listGrouped(EMAIL, true, AVERAGING_ID);

        assertEquals(1, groups.size());
        assertEquals(List.of("live"), names(groups.get(0)));
        assertEquals(1, groups.get(0).count());
    }

    /**
     * A seeded template is locked, so the logic behind a strategy built on one can
     * never change under it - that is the difference a UI has to be able to show,
     * and it is a fact about the TEMPLATE, not about the caller's own row.
     */
    @Test
    void everyRowAndItsHeadingCarryTheTemplatesSystemFlag() {
        StrategyTemplate seeded = template(AVERAGING_ID, "EMA Averaging", true);
        StrategyTemplate authored = template(CROSSOVER_ID, "EMA Crossover", false);
        when(strategies.findByUser_IdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(
                strategy("on a system template", seeded, true),
                strategy("on an authored one", authored, true)));

        List<UserStrategyGroupResponse> groups = service.listGrouped(EMAIL, null, null);

        assertTrue(groups.get(0).strategySystem(), "EMA Averaging is seeded");
        assertFalse(groups.get(1).strategySystem(), "EMA Crossover was published through the API");
        groups.forEach(group -> group.strategies().forEach(row ->
                assertEquals(group.strategySystem(), row.strategySystem(),
                        "a heading and its rows cannot disagree about the template")));
    }

    /**
     * The count of shared computations is a fact about the TEMPLATE, across all
     * users - not about the caller's rows - so it is read from the shared config
     * table and is free to exceed the group's own count.
     */
    @Test
    void eachGroupCarriesItsTemplatesInstanceCount() {
        StrategyTemplate averaging = template(AVERAGING_ID, "EMA Averaging");
        StrategyTemplate crossover = template(CROSSOVER_ID, "EMA Crossover");
        when(strategies.findByUser_IdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(
                strategy("mine", averaging, true),
                strategy("also mine", crossover, true)));
        when(sharedConfigs.countByStrategyIds(any())).thenReturn(
                List.<Object[]>of(new Object[]{AVERAGING_ID, 4L}));

        List<UserStrategyGroupResponse> groups = service.listGrouped(EMAIL, null, null);

        assertEquals(4L, groups.get(0).instanceCount(),
                "more computations than the caller has strategies - other users share it");
        assertEquals(1, groups.get(0).count());
        assertEquals(0L, groups.get(1).instanceCount(),
                "a template no computation exists for is absent from the GROUP BY, not null");
    }

    /**
     * The whole point of batching: a list response asks each question ONCE,
     * however many strategies it covers. Asking per row is a database round trip
     * per row, which is what made this endpoint slow.
     */
    @Test
    void aListAsksForIndicatorsAndCountsOncePerRequestNotOncePerStrategy() {
        StrategyTemplate averaging = template(AVERAGING_ID, "EMA Averaging");
        when(strategies.findByUser_IdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(
                strategy("one", averaging, true),
                strategy("two", averaging, true),
                strategy("three", averaging, true)));

        service.listGrouped(EMAIL, null, null);

        verify(indicatorRows, times(1))
                .findByUserStrategy_IdInOrderByUserStrategy_IdAscDisplayOrderAsc(any());
        verify(subscriptions, times(1)).countByUserStrategyIds(any());
        verify(sharedConfigs, times(1)).countByStrategyIds(any());
        verify(indicatorRows, never()).findByUserStrategy_IdOrderByDisplayOrderAsc(any());
    }

    @Test
    void aUserWithNoStrategiesGetsNoGroups() {
        when(strategies.findByUser_IdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of());

        assertTrue(service.listGrouped(EMAIL, null, null).isEmpty(),
                "an empty array, not a group with nothing in it");
    }

    /** The two shapes read the same rows, so they can never disagree about what the caller has. */
    @Test
    void theGroupedShapeHoldsExactlyTheRowsTheFlatListReturns() {
        StrategyTemplate averaging = template(AVERAGING_ID, "EMA Averaging");
        StrategyTemplate crossover = template(CROSSOVER_ID, "EMA Crossover");
        when(strategies.findByUser_IdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(
                strategy("a", averaging, true),
                strategy("b", crossover, true),
                strategy("c", averaging, true)));

        List<UserStrategyResponse> flat = service.list(EMAIL, null, null);
        List<UserStrategyResponse> grouped = service.listGrouped(EMAIL, null, null).stream()
                .flatMap(group -> group.strategies().stream())
                .toList();

        // WHOLE rows, not their ids. Comparing ids would pass while the grouped
        // shape quietly dropped a field, and every field added to the flat
        // response since has had to be added to this one too - the point of the
        // assertion is that it CANNOT be forgotten. UserStrategyResponse is a
        // record, so this compares every component, including indicatorGroups
        // and fixedParameters.
        assertEquals(flat.size(), grouped.size());
        assertTrue(grouped.containsAll(flat),
                "a row must be byte-identical in both shapes - grouping rearranges, never edits");
    }

    /**
     * The same guarantee stated the other way: the two shapes are built by ONE
     * mapper, so a field cannot exist on the flat row and be missing from the
     * grouped one. If this ever fails, someone has forked the mapping.
     */
    @Test
    void aGroupedRowCarriesEveryFieldTheFlatRowDoes() {
        StrategyTemplate averaging = template(AVERAGING_ID, "EMA Averaging");
        when(strategies.findByUser_IdOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(strategy("only", averaging, true)));

        UserStrategyResponse flat = service.list(EMAIL, null, null).get(0);
        UserStrategyResponse grouped = service.listGrouped(EMAIL, null, null)
                .get(0).strategies().get(0);

        for (RecordComponent component : UserStrategyResponse.class.getRecordComponents()) {
            try {
                assertEquals(component.getAccessor().invoke(flat),
                        component.getAccessor().invoke(grouped),
                        "grouped row differs on " + component.getName());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("could not read " + component.getName(), e);
            }
        }
    }
}

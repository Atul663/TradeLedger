package com.example.tradeLedger;

import com.example.tradeLedger.dto.UserStrategyBulkDeleteResponse;
import com.example.tradeLedger.entity.SharedStrategyConfig;
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
import com.example.tradeLedger.serviceImpl.SubscriptionFanOut;
import com.example.tradeLedger.serviceImpl.SymbolResolver;
import com.example.tradeLedger.serviceImpl.UserStrategyServiceImpl;
import com.example.tradeLedger.serviceImpl.UserStrategyValidator;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deleting the caller's strategies in one sweep.
 *
 * What is worth pinning is that a deployment is a veto on ITS OWN ROW and not on
 * the request: a live one has to survive the sweep untouched while every other
 * strategy is still cleared. The single-strategy delete throws for that case;
 * here the same fact has to arrive as data, because a caller with ten strategies
 * needs to be told which one was left behind and why.
 */
class UserStrategyBulkDeleteTest {

    private static final String EMAIL = "trader@example.com";

    private static final UUID USER_ID = UUID.fromString("00000000-1111-4222-8333-444444444444");
    private static final UUID AVERAGING_ID = UUID.fromString("3f1b0c7e-9a41-4c2e-9f11-2b7d5a6e8c01");
    private static final UUID SHARED_ID = UUID.fromString("5e7a9c1b-3d2f-4a68-9b04-7c1e6f5d8a3b");

    private UserStrategyRepository strategies;
    private StrategySubscriptionRepository subscriptions;
    private SharedStrategyConfigService sharedConfigService;
    private UserStrategyServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);

        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.require(EMAIL)).thenReturn(user);

        strategies = mock(UserStrategyRepository.class);
        subscriptions = mock(StrategySubscriptionRepository.class);
        when(subscriptions.countByUserStrategyIds(any())).thenReturn(List.of());
        sharedConfigService = mock(SharedStrategyConfigService.class);

        SharedStrategyConfigRepository sharedConfigs = mock(SharedStrategyConfigRepository.class);
        when(sharedConfigs.countByStrategyIds(any())).thenReturn(List.of());
        UserStrategyIndicatorRepository indicatorRows = mock(UserStrategyIndicatorRepository.class);
        when(indicatorRows.findByUserStrategy_IdInOrderByUserStrategy_IdAscDisplayOrderAsc(any()))
                .thenReturn(List.of());

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
                sharedConfigService,
                mock(SubscriptionFanOut.class),
                mock(IndicatorParams.class),
                mock(UserStrategyValidator.class),
                mock(SymbolResolver.class),
                mock(RuleTrees.class),
                IndicatorGroupingTest.emptyFixedParameters(),
                new JsonSupport(new ObjectMapper()));
    }

    private static StrategyTemplate template() {
        StrategyTemplate template = new StrategyTemplate();
        template.setId(AVERAGING_ID);
        template.setName("EMA Averaging");
        template.setSystem(true);
        return template;
    }

    private UserStrategy strategy(UUID id, String name, SharedStrategyConfig sharedConfig) {
        UserStrategy strategy = new UserStrategy();
        strategy.setId(id);
        strategy.setUser(user);
        strategy.setStrategy(template());
        strategy.setName(name);
        strategy.setActive(true);
        strategy.setSharedConfig(sharedConfig);
        return strategy;
    }

    private static SharedStrategyConfig sharedConfig(UUID id) {
        SharedStrategyConfig config = new SharedStrategyConfig();
        config.setId(id);
        return config;
    }

    /** An {@code [id, count]} pair, the shape the batched count query returns. */
    private static Object[] count(UUID id, long deployments) {
        return new Object[]{id, deployments};
    }

    @Test
    void deletesWhatItCanAndSkipsWhatIsStillDeployed() {
        UUID free = UUID.fromString("a1000000-1111-4222-8333-444444444444");
        UUID deployed = UUID.fromString("b2000000-1111-4222-8333-444444444444");
        when(strategies.findByUser_IdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(
                strategy(free, "NIFTY calls OTM5", null),
                strategy(deployed, "NIFTY 21/9 both sides", null)));
        // Explicit element type: a lone Object[] would otherwise be spread as varargs.
        when(subscriptions.countByUserStrategyIds(any()))
                .thenReturn(List.<Object[]>of(count(deployed, 3L)));

        UserStrategyBulkDeleteResponse response = service.deleteAll(EMAIL, null, null);

        assertEquals(2, response.requested());
        assertEquals(1, response.deleted());
        assertEquals(1, response.skipped());
        assertEquals(response.requested(), response.results().size(),
                "every strategy the filters matched reports an outcome");

        UserStrategyBulkDeleteResponse.Item first = response.results().get(0);
        assertEquals(UserStrategyBulkDeleteResponse.STATUS_DELETED, first.status());
        assertEquals(0L, first.deployments());
        assertNull(first.error());

        UserStrategyBulkDeleteResponse.Item second = response.results().get(1);
        assertEquals(UserStrategyBulkDeleteResponse.STATUS_SKIPPED, second.status());
        assertEquals(3L, second.deployments());
        assertNotNull(second.error());
        assertTrue(second.error().contains("deployed on 3 account(s)"),
                "a skipped row explains itself the way the single delete would");
        assertEquals(AVERAGING_ID, second.strategyId());
        assertEquals("EMA Averaging", second.strategyName());

        // The veto is per row: the free one goes, the deployed one is left
        // standing rather than the whole sweep being refused.
        verify(strategies).delete(argThat(row -> free.equals(row.getId())));
        verify(strategies, never()).delete(argThat(row -> deployed.equals(row.getId())));
    }

    /**
     * Several strategies can sit on one shared computation. It is retired once,
     * and only after the deletes have been flushed - asked any earlier it still
     * looks occupied by rows that are about to vanish.
     */
    @Test
    void retiresEachFreedComputationOnceAfterTheDeletesAreFlushed() {
        SharedStrategyConfig shared = sharedConfig(SHARED_ID);
        when(strategies.findByUser_IdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(
                strategy(UUID.randomUUID(), "one", shared),
                strategy(UUID.randomUUID(), "two", shared)));

        service.deleteAll(EMAIL, null, null);

        verify(strategies, times(2)).delete(any(UserStrategy.class));
        verify(strategies).flush();
        verify(sharedConfigService, times(1)).retireIfOrphaned(SHARED_ID);
    }

    /** Nothing matched is not an error - and asks the database nothing further. */
    @Test
    void matchingNothingIsAnEmptySuccess() {
        when(strategies.findByUser_IdAndActiveOrderByCreatedAtAsc(USER_ID, false))
                .thenReturn(List.of());

        UserStrategyBulkDeleteResponse response = service.deleteAll(EMAIL, false, null);

        assertEquals(0, response.requested());
        assertEquals(0, response.deleted());
        assertEquals(0, response.skipped());
        assertTrue(response.results().isEmpty());
        verify(subscriptions, never()).countByUserStrategyIds(any());
        verify(strategies, never()).delete(any(UserStrategy.class));
    }

    /** The filters are the list's, so a sweep can be narrowed to one template. */
    @Test
    void theTemplateFilterNarrowsTheSweep() {
        UUID only = UUID.fromString("c3000000-1111-4222-8333-444444444444");
        when(strategies.findByUser_IdAndStrategy_IdOrderByCreatedAtAsc(USER_ID, AVERAGING_ID))
                .thenReturn(List.of(strategy(only, "BANKNIFTY 21/9", null)));

        UserStrategyBulkDeleteResponse response = service.deleteAll(EMAIL, null, AVERAGING_ID);

        assertEquals(1, response.deleted());
        assertEquals(only, response.results().get(0).id());
        verify(strategies, never()).findByUser_IdOrderByCreatedAtAsc(any());
    }
}

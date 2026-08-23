package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.StrategySubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StrategySubscriptionRepository extends JpaRepository<StrategySubscription, UUID> {

    List<StrategySubscription> findByUser_IdOrderByCreatedAtAsc(UUID userId);

    /** Ownership-scoped read: a subscription is never reachable across users. */
    Optional<StrategySubscription> findByIdAndUser_Id(UUID id, UUID userId);

    /** Every broker one saved strategy is deployed on. */
    List<StrategySubscription> findByUserStrategy_IdOrderByCreatedAtAsc(UUID userStrategyId);

    /** UNIQUE (user_strategy_id, trading_account_id) - one deployment per account. */
    Optional<StrategySubscription> findByUserStrategy_IdAndTradingAccount_Id(
            UUID userStrategyId, UUID tradingAccountId);

    List<StrategySubscription> findByActiveTrue();

    /**
     * Refcount driving shared-config retirement.
     *
     * Reached through {@code user_strategies} rather than a second FK on this row:
     * the config a deployment runs is a property of the strategy, and storing the
     * pointer twice is how the two copies start to disagree.
     */
    long countByUserStrategy_SharedConfig_IdAndActiveTrue(UUID sharedConfigId);

    long countByUserStrategy_SharedConfig_Id(UUID sharedConfigId);

    long countByUserStrategy_IdAndActiveTrue(UUID userStrategyId);

    long countByUserStrategy_Id(UUID userStrategyId);

    long countByTradingAccount_IdAndActiveTrue(UUID tradingAccountId);

    boolean existsByRiskProfile_Id(UUID riskProfileId);
}

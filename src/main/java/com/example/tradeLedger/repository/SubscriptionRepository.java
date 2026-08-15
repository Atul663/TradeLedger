package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByUser_IdOrderByCreatedAtAsc(UUID userId);

    /** Ownership-scoped read: a subscription is never reachable across users. */
    Optional<Subscription> findByIdAndUser_Id(UUID id, UUID userId);

    /** UNIQUE (strategy_instance_id, trading_account_id) - the per-leg guarantee. */
    Optional<Subscription> findByStrategyInstance_IdAndTradingAccount_Id(UUID strategyInstanceId, UUID tradingAccountId);

    List<Subscription> findByActiveTrue();

    /** Refcount driving instance retirement. */
    long countByStrategyInstance_IdAndActiveTrue(UUID strategyInstanceId);

    long countByStrategyInstance_Id(UUID strategyInstanceId);

    long countByTradingAccount_IdAndActiveTrue(UUID tradingAccountId);

    boolean existsByRiskProfile_Id(UUID riskProfileId);
}

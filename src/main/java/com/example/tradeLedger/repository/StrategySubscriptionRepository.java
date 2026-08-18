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

    /** UNIQUE (shared_config_id, trading_account_id) - the per-leg guarantee. */
    Optional<StrategySubscription> findBySharedConfig_IdAndTradingAccount_Id(UUID sharedConfigId, UUID tradingAccountId);

    List<StrategySubscription> findByActiveTrue();

    /** Refcount driving instance retirement. */
    long countBySharedConfig_IdAndActiveTrue(UUID sharedConfigId);

    long countBySharedConfig_Id(UUID sharedConfigId);

    long countByTradingAccount_IdAndActiveTrue(UUID tradingAccountId);

    boolean existsByRiskProfile_Id(UUID riskProfileId);
}

package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.UserStrategyIndicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserStrategyIndicatorRepository extends JpaRepository<UserStrategyIndicator, UUID> {

    List<UserStrategyIndicator> findByUserStrategy_IdOrderByDisplayOrderAsc(UUID userStrategyId);

    /** Ownership-scoped: an indicator row is only addressable through its own user strategy. */
    Optional<UserStrategyIndicator> findByIdAndUserStrategy_Id(UUID id, UUID userStrategyId);

    /** The unique key - slot is null for a template that uses each indicator once. */
    Optional<UserStrategyIndicator> findByUserStrategy_IdAndIndicator_IdAndSlot(
            UUID userStrategyId, UUID indicatorId, String slot);

    /** Slot-less lookup, for the common case where the caller sends only an indicator id. */
    List<UserStrategyIndicator> findByUserStrategy_IdAndIndicator_Id(UUID userStrategyId, UUID indicatorId);

    /** Guards indicator deletion: a primitive somebody's strategy is tuned on is in use. */
    long countByIndicator_Id(UUID indicatorId);
}

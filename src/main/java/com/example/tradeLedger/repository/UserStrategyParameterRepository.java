package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.UserStrategyParameter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserStrategyParameterRepository extends JpaRepository<UserStrategyParameter, UUID> {

    /** Every override the user made, both levels, in one read. */
    List<UserStrategyParameter> findByUserStrategy_Id(UUID userStrategyId);

    /** The indicator-level override of one knob. */
    Optional<UserStrategyParameter> findByUserStrategyIndicator_IdAndParameter_Id(
            UUID userStrategyIndicatorId, Long parameterId);

    /** The strategy-level override of one knob - the rows with no indicator. */
    Optional<UserStrategyParameter> findByUserStrategy_IdAndParameter_IdAndUserStrategyIndicatorIsNull(
            UUID userStrategyId, Long parameterId);

    /** Reports how many users pinned a catalog knob, before it is retired. */
    long countByParameter_Id(Long parameterId);
}

package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.StrategyParamDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StrategyParamDefRepository extends JpaRepository<StrategyParamDef, Long> {

    List<StrategyParamDef> findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(UUID strategyId);

    List<StrategyParamDef> findByStrategy_IdAndScopeOrderByDisplayOrderAsc(UUID strategyId, String scope);

    Optional<StrategyParamDef> findByStrategy_IdAndParameterKey(UUID strategyId, String parameterKey);

    /** Ownership-scoped read: a knob is only addressable through its own strategy. */
    Optional<StrategyParamDef> findByIdAndStrategy_Id(Long id, UUID strategyId);

    boolean existsByStrategy_IdAndParameterKey(UUID strategyId, String parameterKey);

    long countByStrategy_Id(UUID strategyId);
}

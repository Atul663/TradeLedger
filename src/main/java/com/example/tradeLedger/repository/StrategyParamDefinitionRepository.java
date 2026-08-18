package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.StrategyParamDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StrategyParamDefinitionRepository extends JpaRepository<StrategyParamDefinition, Long> {

    List<StrategyParamDefinition> findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(UUID strategyId);

    List<StrategyParamDefinition> findByStrategy_IdAndScopeOrderByDisplayOrderAsc(UUID strategyId, String scope);

    Optional<StrategyParamDefinition> findByStrategy_IdAndParameterKey(UUID strategyId, String parameterKey);

    /** Ownership-scoped read: a knob is only addressable through its own strategy. */
    Optional<StrategyParamDefinition> findByIdAndStrategy_Id(Long id, UUID strategyId);

    boolean existsByStrategy_IdAndParameterKey(UUID strategyId, String parameterKey);

    long countByStrategy_Id(UUID strategyId);
}

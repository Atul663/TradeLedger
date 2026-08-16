package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.StrategyParameter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Strategy to parameter, by id. Answers "which parameters belong to this strategy directly". */
public interface StrategyParameterRepository extends JpaRepository<StrategyParameter, Long> {

    List<StrategyParameter> findByStrategy_IdOrderByDisplayOrderAscIdAsc(UUID strategyId);

    /** The reverse direction: every strategy using this parameter. */
    List<StrategyParameter> findByParameter_IdOrderByStrategy_NameAsc(Long parameterId);

    Optional<StrategyParameter> findByStrategy_IdAndParameter_Code(UUID strategyId, String code);

    boolean existsByStrategy_IdAndParameter_Id(UUID strategyId, Long parameterId);

    boolean existsByParameter_Id(Long parameterId);

    void deleteByStrategy_Id(UUID strategyId);
}

package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.StrategyParameterLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** StrategyTemplate to parameter, by id. Answers "which parameters belong to this strategy directly". */
public interface StrategyParameterLinkRepository extends JpaRepository<StrategyParameterLink, Long> {

    List<StrategyParameterLink> findByStrategy_IdOrderByDisplayOrderAscIdAsc(UUID strategyId);

    /** The reverse direction: every strategy using this parameter. */
    List<StrategyParameterLink> findByParameter_IdOrderByStrategy_NameAsc(Long parameterId);

    Optional<StrategyParameterLink> findByStrategy_IdAndParameter_Code(UUID strategyId, String code);

    boolean existsByStrategy_IdAndParameter_Id(UUID strategyId, Long parameterId);

    boolean existsByParameter_Id(Long parameterId);

    void deleteByStrategy_Id(UUID strategyId);
}

package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.IndicatorParameter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Indicator to parameter, by id. Answers "which parameters does this indicator have". */
public interface IndicatorParameterRepository extends JpaRepository<IndicatorParameter, Long> {

    List<IndicatorParameter> findByIndicator_IdOrderByDisplayOrderAscIdAsc(UUID indicatorId);

    /** The reverse direction: every indicator using this parameter. */
    List<IndicatorParameter> findByParameter_IdOrderByIndicator_NameAsc(Long parameterId);

    Optional<IndicatorParameter> findByIndicator_IdAndParameter_Code(UUID indicatorId, String code);

    boolean existsByParameter_Id(Long parameterId);

    void deleteByIndicator_Id(UUID indicatorId);
}

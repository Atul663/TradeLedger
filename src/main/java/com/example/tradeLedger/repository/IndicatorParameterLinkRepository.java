package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.IndicatorParameterLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Indicator to parameter, by id. Answers "which parameters does this indicator have". */
public interface IndicatorParameterLinkRepository extends JpaRepository<IndicatorParameterLink, Long> {

    List<IndicatorParameterLink> findByIndicator_IdOrderByDisplayOrderAscIdAsc(UUID indicatorId);

    /** The reverse direction: every indicator using this parameter. */
    List<IndicatorParameterLink> findByParameter_IdOrderByIndicator_NameAsc(Long parameterId);

    Optional<IndicatorParameterLink> findByIndicator_IdAndParameter_Code(UUID indicatorId, String code);

    /** The link a user override displaces - "does this knob belong to this indicator". */
    Optional<IndicatorParameterLink> findByIndicator_IdAndParameter_Id(UUID indicatorId, Long parameterId);

    boolean existsByParameter_Id(Long parameterId);

    void deleteByIndicator_Id(UUID indicatorId);
}

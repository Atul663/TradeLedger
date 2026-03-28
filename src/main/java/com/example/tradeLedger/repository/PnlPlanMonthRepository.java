package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.PnlPlanMonth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PnlPlanMonthRepository extends JpaRepository<PnlPlanMonth, Long> {

    List<PnlPlanMonth> findByPlan_IdOrderByMonthSequenceAsc(Long planId);

    List<PnlPlanMonth> findByPlan_User_IdOrderByPlan_IdAscMonthSequenceAsc(Long userId);

    Optional<PnlPlanMonth> findFirstByPlan_IdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long planId,
            LocalDate tradeDateStart,
            LocalDate tradeDateEnd
    );

    Optional<PnlPlanMonth> findByIdAndPlan_User_Id(Long id, Long userId);

    Optional<PnlPlanMonth> findByPlan_IdAndMonthLabelIgnoreCase(Long planId, String monthLabel);
}

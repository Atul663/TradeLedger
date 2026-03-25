package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.PnlTradingDay;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PnlTradingDayRepository extends JpaRepository<PnlTradingDay, Long> {

    List<PnlTradingDay> findByPlanMonth_IdOrderByTradingDaySeqAsc(Long planMonthId);

    @EntityGraph(attributePaths = {"dailyFact"})
    List<PnlTradingDay> findWithDailyFactByPlanMonth_IdOrderByTradingDaySeqAsc(Long planMonthId);

    Optional<PnlTradingDay> findByPlanMonth_IdAndTradeDate(Long planMonthId, LocalDate tradeDate);

    @EntityGraph(attributePaths = {"dailyFact", "planMonth"})
    List<PnlTradingDay> findByPlanMonth_Plan_IdOrderByPlanMonth_MonthSequenceAscTradingDaySeqAsc(Long planId);
}

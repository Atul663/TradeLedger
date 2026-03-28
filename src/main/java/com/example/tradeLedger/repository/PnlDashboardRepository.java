package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.PnlPlan;
import com.example.tradeLedger.entity.PnlPlanMonth;
import com.example.tradeLedger.entity.PnlTradingDay;
import com.example.tradeLedger.entity.UserDetails;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PnlDashboardRepository {

    private final PnlPlanRepository pnlPlanRepository;
    private final PnlPlanMonthRepository pnlPlanMonthRepository;
    private final PnlTradingDayRepository pnlTradingDayRepository;

    public PnlDashboardRepository(
            PnlPlanRepository pnlPlanRepository,
            PnlPlanMonthRepository pnlPlanMonthRepository,
            PnlTradingDayRepository pnlTradingDayRepository
    ) {
        this.pnlPlanRepository = pnlPlanRepository;
        this.pnlPlanMonthRepository = pnlPlanMonthRepository;
        this.pnlTradingDayRepository = pnlTradingDayRepository;
    }

    public PnlPlan findActivePlan(UserDetails user, LocalDate tradeDate, String providedType) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User is required.");
        }
        final String planType = (providedType == null || providedType.isBlank()) ? "FNO" : providedType;

        return pnlPlanRepository
                .findFirstByUser_IdAndPlanTypeAndActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                        user.getId(),
                        planType,
                        tradeDate,
                        tradeDate
                )
                .orElseThrow(() -> new IllegalStateException("No active P&L plan found for trade date " + tradeDate + "."));
    }

    public List<PnlPlanMonth> findPlanMonths(Long planId) {
        return pnlPlanMonthRepository.findByPlan_IdOrderByMonthSequenceAsc(planId);
    }

    public PnlPlanMonth findPlanMonth(Long planId, LocalDate tradeDate) {
        return pnlPlanMonthRepository
                .findFirstByPlan_IdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(planId, tradeDate, tradeDate)
                .orElseThrow(() -> new IllegalStateException("No plan month found for trade date " + tradeDate + "."));
    }

    public Map<Long, List<PnlTradingDay>> findTradingDaysGroupedByMonth(Long planId, List<PnlPlanMonth> months) {
        Map<Long, List<PnlTradingDay>> monthTradingDays = new LinkedHashMap<>();
        for (PnlPlanMonth month : months) {
            monthTradingDays.put(month.getId(), new ArrayList<>());
        }

        List<PnlTradingDay> tradingDays = pnlTradingDayRepository
                .findByPlanMonth_Plan_IdOrderByPlanMonth_MonthSequenceAscTradingDaySeqAsc(planId);

        for (PnlTradingDay tradingDay : tradingDays) {
            monthTradingDays.computeIfAbsent(tradingDay.getPlanMonth().getId(), ignored -> new ArrayList<>())
                    .add(tradingDay);
        }

        return monthTradingDays;
    }
}

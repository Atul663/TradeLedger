package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.PnlDailyCalculationDto;
import com.example.tradeLedger.dto.PnlMonthSummaryDto;
import com.example.tradeLedger.dto.PnlPlanDto;
import com.example.tradeLedger.dto.PnlPlanMonthDto;
import com.example.tradeLedger.dto.PnlWorkbookViewDto;
import com.example.tradeLedger.entity.PnlPlan;
import com.example.tradeLedger.entity.PnlPlanMonth;
import com.example.tradeLedger.entity.PnlTradingDay;
import com.example.tradeLedger.entity.UserDetails;
import com.example.tradeLedger.repository.PnlDashboardRepository;
import com.example.tradeLedger.service.PnlDashboardService;
import com.example.tradeLedger.service.PnlLedgerService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@Transactional(Transactional.TxType.SUPPORTS)
public class PnlDashboardServiceImpl implements PnlDashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final PnlDashboardRepository pnlDashboardRepository;
    private final PnlLedgerService pnlLedgerService;

    public PnlDashboardServiceImpl(PnlDashboardRepository pnlDashboardRepository, PnlLedgerService pnlLedgerService) {
        this.pnlDashboardRepository = pnlDashboardRepository;
        this.pnlLedgerService = pnlLedgerService;
    }

    @Override
    public PnlWorkbookViewDto getWorkbookView(UserDetails user, LocalDate tradeDate, String planType) {
        LocalDate effectiveTradeDate = defaultTradeDate(tradeDate);
        PnlPlan plan = pnlDashboardRepository.findActivePlan(user, effectiveTradeDate, planType);
        List<PnlPlanMonth> months = pnlDashboardRepository.findPlanMonths(plan.getId());
        PnlPlanMonth currentMonth = pnlDashboardRepository.findPlanMonth(plan.getId(), effectiveTradeDate);
        List<PnlMonthSummaryDto> yearSummary = buildYearSummary(
                plan,
                months,
                pnlDashboardRepository.findTradingDaysGroupedByMonth(plan.getId(), months)
        );

        PnlWorkbookViewDto workbookView = new PnlWorkbookViewDto();
        workbookView.setPlan(toPlanDto(plan, months));
        workbookView.setMonthLabel(currentMonth.getMonthLabel());
        workbookView.setTradeDate(effectiveTradeDate);
        workbookView.setCurrentMonthSummary(findCurrentMonthSummary(yearSummary, currentMonth.getMonthSequence()));
        return workbookView;
    }

    @Override
    public PnlMonthSummaryDto getCurrentMonthSummary(UserDetails user, LocalDate tradeDate, String planType) {
        LocalDate effectiveTradeDate = defaultTradeDate(tradeDate);
        PnlPlan plan = pnlDashboardRepository.findActivePlan(user, effectiveTradeDate, planType);
        List<PnlPlanMonth> months = pnlDashboardRepository.findPlanMonths(plan.getId());
        PnlPlanMonth currentMonth = pnlDashboardRepository.findPlanMonth(plan.getId(), effectiveTradeDate);
        List<PnlMonthSummaryDto> yearSummary = buildYearSummary(
                plan,
                months,
                pnlDashboardRepository.findTradingDaysGroupedByMonth(plan.getId(), months)
        );

        return findCurrentMonthSummary(yearSummary, currentMonth.getMonthSequence());
    }

    @Override
    public List<PnlMonthSummaryDto> getYearSummary(UserDetails user, LocalDate tradeDate, String planType) {
        LocalDate effectiveTradeDate = defaultTradeDate(tradeDate);
        PnlPlan plan = pnlDashboardRepository.findActivePlan(user, effectiveTradeDate, planType);
        List<PnlPlanMonth> months = pnlDashboardRepository.findPlanMonths(plan.getId());
        return buildYearSummary(plan, months, pnlDashboardRepository.findTradingDaysGroupedByMonth(plan.getId(), months));
    }

    @Override
    public List<PnlDailyCalculationDto> getMonthSheet(UserDetails user, LocalDate tradeDate, String planType) {
        LocalDate effectiveTradeDate = defaultTradeDate(tradeDate);
        PnlPlan plan = pnlDashboardRepository.findActivePlan(user, effectiveTradeDate, planType);
        pnlLedgerService.ensureMonthlyStructure(user, effectiveTradeDate, planType);

        List<PnlPlanMonth> months = pnlDashboardRepository.findPlanMonths(plan.getId());
        PnlPlanMonth currentMonth = pnlDashboardRepository.findPlanMonth(plan.getId(), effectiveTradeDate);
        Map<Long, List<PnlTradingDay>> monthTradingDays = pnlDashboardRepository.findTradingDaysGroupedByMonth(plan.getId(), months);
        List<PnlMonthSummaryDto> yearSummary = buildYearSummary(plan, months, monthTradingDays);
        return buildDailySheet(currentMonth, monthTradingDays.getOrDefault(currentMonth.getId(), List.of()), yearSummary);
    }

    private PnlMonthSummaryDto findCurrentMonthSummary(List<PnlMonthSummaryDto> yearSummary, Integer monthSequence) {
        return yearSummary.stream()
                .filter(summary -> summary.getMonthSequence().equals(monthSequence))
                .findFirst()
                .orElse(null);
    }

    private List<PnlMonthSummaryDto> buildYearSummary(
            PnlPlan plan,
            List<PnlPlanMonth> months,
            Map<Long, List<PnlTradingDay>> monthTradingDays
    ) {
        List<PnlMonthSummaryDto> summaries = new ArrayList<>();
        BigDecimal achievedBeforeMonth = ZERO;
        BigDecimal runningYtd = ZERO;

        for (int i = 0; i < months.size(); i++) {
            PnlPlanMonth month = months.get(i);
            BigDecimal monthAchieved = calculateMonthAchieved(monthTradingDays.getOrDefault(month.getId(), List.of()));
            BigDecimal monthTarget = effectiveMonthTarget(plan, month, achievedBeforeMonth, months.size() - i);

            runningYtd = runningYtd.add(monthAchieved);

            PnlMonthSummaryDto summary = new PnlMonthSummaryDto();
            summary.setMonthSequence(month.getMonthSequence());
            summary.setMonthLabel(month.getMonthLabel());
            summary.setTradingDaysPlanned(month.getTradingDaysPlanned());
            summary.setMonthTarget(monthTarget);
            summary.setMonthAchieved(monthAchieved);
            summary.setMonthBalance(scale(monthTarget.subtract(monthAchieved)));
            summary.setMonthAchievedPct(percentage(monthAchieved, monthTarget));
            summary.setYtdAchieved(scale(runningYtd));
            summary.setYtdBalance(scale(scale(plan.getAnnualTarget()).subtract(runningYtd)));
            summaries.add(summary);

            achievedBeforeMonth = achievedBeforeMonth.add(monthAchieved);
        }

        return summaries;
    }

    private List<PnlDailyCalculationDto> buildDailySheet(
            PnlPlanMonth currentMonth,
            List<PnlTradingDay> tradingDays,
            List<PnlMonthSummaryDto> yearSummary
    ) {
        if (tradingDays == null) {
            return List.of();
        }

        List<PnlTradingDay> sortedTradingDays = new ArrayList<>(tradingDays);
        sortedTradingDays.sort(Comparator.comparing(PnlTradingDay::getTradingDaySeq));

        PnlMonthSummaryDto currentMonthSummary = findCurrentMonthSummary(yearSummary, currentMonth.getMonthSequence());
        if (currentMonthSummary == null) {
            return List.of();
        }

        BigDecimal achievedBeforeDay = ZERO;
        BigDecimal achievedToDate = ZERO;
        int pendingTradingDays = 0;
        List<PnlDailyCalculationDto> dailySheet = new ArrayList<>();

        for (PnlTradingDay tradingDay : sortedTradingDays) {
            BigDecimal actualPnl = tradingDay.getDailyFact() != null ? scale(tradingDay.getDailyFact().getNetPnl()) : null;
            int remainingTradingDays = currentMonth.getTradingDaysPlanned() - tradingDay.getTradingDaySeq() + 1;
            BigDecimal storedDailyPlan = tradingDay.getDailyTarget() != null ? scale(tradingDay.getDailyTarget()) : null;

            PnlDailyCalculationDto dailyCalculation = new PnlDailyCalculationDto();
            dailyCalculation.setTradeDate(tradingDay.getTradeDate());
            dailyCalculation.setTradingDaySequence(tradingDay.getTradingDaySeq());
            dailyCalculation.setRemainingTradingDays(remainingTradingDays);
            dailyCalculation.setActualPnl(actualPnl);
            dailyCalculation.setRemark(tradingDay.getRemark());

            if (actualPnl != null) {
                BigDecimal dailyPlan = storedDailyPlan != null
                        ? storedDailyPlan
                        : historicalDailyPlan(
                                currentMonthSummary.getMonthTarget(),
                                currentMonth.getTradingDaysPlanned(),
                                tradingDay.getTradingDaySeq(),
                                achievedBeforeDay
                        );
                dailyCalculation.setDailyPlan(dailyPlan);
                BigDecimal mtdAchieved = scale(achievedBeforeDay.add(actualPnl));
                dailyCalculation.setMtdAchieved(mtdAchieved);
                dailyCalculation.setYtaMonth(scale(currentMonthSummary.getMonthTarget().subtract(mtdAchieved)));
                dailyCalculation.setMtdPct(percentage(mtdAchieved, currentMonthSummary.getMonthTarget()));
                if (dailyPlan != null) {
                    dailyCalculation.setXtraShortfall(scale(actualPnl.subtract(dailyPlan)));
                }
                achievedBeforeDay = mtdAchieved;
                achievedToDate = mtdAchieved;
            } else {
                pendingTradingDays++;
                dailyCalculation.setDailyPlan(storedDailyPlan);
                dailyCalculation.setMtdAchieved(null);
                dailyCalculation.setYtaMonth(null);
                dailyCalculation.setMtdPct(null);
                dailyCalculation.setXtraShortfall(null);
            }

            dailySheet.add(dailyCalculation);
        }

        BigDecimal pendingDailyPlan = divide(
                scale(currentMonthSummary.getMonthTarget().subtract(achievedToDate)),
                pendingTradingDays,
                2
        );
        if (pendingTradingDays > 0) {
            for (PnlDailyCalculationDto dailyCalculation : dailySheet) {
                if (dailyCalculation.getActualPnl() == null && dailyCalculation.getDailyPlan() == null) {
                    dailyCalculation.setDailyPlan(pendingDailyPlan);
                }
            }
        }

        return dailySheet;
    }

    private BigDecimal historicalDailyPlan(
            BigDecimal monthTarget,
            int tradingDaysPlanned,
            int tradingDaySequence,
            BigDecimal achievedBeforeDay
    ) {
        if (tradingDaySequence == 1) {
            return divide(monthTarget, tradingDaysPlanned, 2);
        }

        int remainingTradingDays = tradingDaysPlanned - tradingDaySequence + 1;
        return divide(monthTarget.subtract(achievedBeforeDay), remainingTradingDays, 2);
    }

    private BigDecimal calculateMonthAchieved(List<PnlTradingDay> tradingDays) {
        BigDecimal total = ZERO;
        for (PnlTradingDay tradingDay : tradingDays) {
            if (tradingDay.getDailyFact() != null && tradingDay.getDailyFact().getNetPnl() != null) {
                total = total.add(scale(tradingDay.getDailyFact().getNetPnl()));
            }
        }
        return scale(total);
    }

    private PnlPlanDto toPlanDto(PnlPlan plan, List<PnlPlanMonth> months) {
        PnlPlanDto dto = new PnlPlanDto();
        dto.setId(plan.getId());
        dto.setPlanName(plan.getPlanName());
        dto.setStartDate(plan.getStartDate());
        dto.setEndDate(plan.getEndDate());
        dto.setAnnualTarget(scale(plan.getAnnualTarget()));
        dto.setCurrency(plan.getCurrency());
        dto.setActive(plan.isActive());
        dto.setPlanType(plan.getPlanType());
        dto.setStartingCapital(scale(plan.getStartingCapital()));
        dto.setTotalAchievedAmount(scale(plan.getTotalAchievedAmount()));
        dto.setCurrentCapital(scale(plan.getStartingCapital().add(plan.getTotalAchievedAmount())));
        dto.setMonths(toPlanMonthDtos(months));
        return dto;
    }

    private List<PnlPlanMonthDto> toPlanMonthDtos(List<PnlPlanMonth> months) {
        List<PnlPlanMonthDto> monthDtos = new ArrayList<>();
        for (PnlPlanMonth month : months) {
            PnlPlanMonthDto monthDto = new PnlPlanMonthDto();
            monthDto.setId(month.getId());
            monthDto.setMonthSequence(month.getMonthSequence());
            monthDto.setMonthLabel(month.getMonthLabel());
            monthDto.setStartDate(month.getStartDate());
            monthDto.setEndDate(month.getEndDate());
            monthDto.setTradingDaysPlanned(month.getTradingDaysPlanned());
            monthDto.setAllocatedTarget(month.getAllocatedTarget() != null ? scale(month.getAllocatedTarget()) : null);
            monthDto.setManualTarget(month.getManualTarget() != null ? scale(month.getManualTarget()) : null);
            monthDtos.add(monthDto);
        }
        return monthDtos;
    }

    private LocalDate defaultTradeDate(LocalDate tradeDate) {
        return tradeDate != null ? tradeDate : LocalDate.now();
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal value, int divisor, int scale) {
        if (value == null || divisor == 0) {
            return null;
        }
        return value.divide(BigDecimal.valueOf(divisor), scale, RoundingMode.HALF_UP);
    }

    private BigDecimal effectiveMonthTarget(
            PnlPlan plan,
            PnlPlanMonth month,
            BigDecimal achievedBeforeMonth,
            int remainingMonths
    ) {
        if (month.getAllocatedTarget() != null) {
            return scale(month.getAllocatedTarget());
        }
        if (month.getManualTarget() != null) {
            return scale(month.getManualTarget());
        }
        return divide(scale(plan.getAnnualTarget()).subtract(achievedBeforeMonth), remainingMonths, 2);
    }
}

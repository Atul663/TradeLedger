package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.ObligationDto;
import com.example.tradeLedger.dto.PnlDailyCalculationDto;
import com.example.tradeLedger.dto.PnlManualEntryRequestDto;
import com.example.tradeLedger.dto.PnlMonthSummaryDto;
import com.example.tradeLedger.dto.PnlMonthTargetUpdateDto;
import com.example.tradeLedger.dto.PnlPlanDto;
import com.example.tradeLedger.dto.PnlPlanMonthDto;
import com.example.tradeLedger.dto.PnlPlanRequestDto;
import com.example.tradeLedger.dto.PnlProcessResultDto;
import com.example.tradeLedger.dto.PnlWorkbookViewDto;
import com.example.tradeLedger.entity.PnlDailyFact;
import com.example.tradeLedger.entity.PnlImportSource;
import com.example.tradeLedger.entity.PnlPlan;
import com.example.tradeLedger.entity.PnlPlanMonth;
import com.example.tradeLedger.entity.PnlTradingDay;
import com.example.tradeLedger.entity.TradingCalendar;
import com.example.tradeLedger.entity.UserDetails;
import com.example.tradeLedger.repository.PnlDailyFactRepository;
import com.example.tradeLedger.repository.PnlImportSourceRepository;
import com.example.tradeLedger.repository.PnlPlanMonthRepository;
import com.example.tradeLedger.repository.PnlPlanRepository;
import com.example.tradeLedger.repository.PnlTradingDayRepository;
import com.example.tradeLedger.repository.TradingCalendarRepository;
import com.example.tradeLedger.service.PnlLedgerService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;


@Service
@Transactional
public class PnlLedgerServiceImpl implements PnlLedgerService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MMMyy", Locale.ENGLISH);

    private final PnlPlanRepository pnlPlanRepository;
    private final PnlPlanMonthRepository pnlPlanMonthRepository;
    private final PnlTradingDayRepository pnlTradingDayRepository;
    private final PnlDailyFactRepository pnlDailyFactRepository;
    private final PnlImportSourceRepository pnlImportSourceRepository;
    private final TradingCalendarRepository tradingCalendarRepository;

    public PnlLedgerServiceImpl(
            PnlPlanRepository pnlPlanRepository,
            PnlPlanMonthRepository pnlPlanMonthRepository,
            PnlTradingDayRepository pnlTradingDayRepository,
            PnlDailyFactRepository pnlDailyFactRepository,
            PnlImportSourceRepository pnlImportSourceRepository,
            TradingCalendarRepository tradingCalendarRepository
    ) {
        this.pnlPlanRepository = pnlPlanRepository;
        this.pnlPlanMonthRepository = pnlPlanMonthRepository;
        this.pnlTradingDayRepository = pnlTradingDayRepository;
        this.pnlDailyFactRepository = pnlDailyFactRepository;
        this.pnlImportSourceRepository = pnlImportSourceRepository;
        this.tradingCalendarRepository = tradingCalendarRepository;
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public PnlProcessResultDto saveObligationAndCalculate(
            UserDetails user,
            LocalDate tradeDate,
            ObligationDto obligation,
            int numberOfTrades,
            String gmailMessageId,
            String attachmentChecksum,
            String planType
    ) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User is required for persistence.");
        }
        if (tradeDate == null) {
            throw new IllegalArgumentException("Trade date could not be determined from the statement.");
        }

        Optional<PnlImportSource> duplicateImport = findProcessedDuplicate(user.getId(), tradeDate, gmailMessageId, attachmentChecksum);
        if (duplicateImport.isPresent()) {
            return buildProcessResult(user, tradeDate, duplicateImport.get(), true, planType);
        }

        PnlImportSource importSource = findReusableImport(user.getId(), tradeDate, gmailMessageId, attachmentChecksum)
                .orElseGet(PnlImportSource::new);

        importSource.setUser(user);
        importSource.setTradeDate(tradeDate);
        importSource.setGmailMessageId(gmailMessageId);
        importSource.setAttachmentChecksum(attachmentChecksum);
        importSource.setStatus("PENDING");
        importSource.setErrorMessage(null);
        importSource.setProcessedAt(LocalDateTime.now());
        importSource = pnlImportSourceRepository.save(importSource);

        try {
            PnlPlan plan = resolveActivePlan(user, tradeDate, planType);

            ensurePlanMonths(plan);

            PnlPlanMonth planMonth = pnlPlanMonthRepository
                    .findFirstByPlan_IdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(plan.getId(), tradeDate, tradeDate)
                    .orElseThrow(() -> new IllegalStateException("No plan month found for trade date " + tradeDate + "."));

            ensureTradingDaysForTradeDate(plan, planMonth, tradeDate);

            PnlTradingDay tradingDay = pnlTradingDayRepository.findByPlanMonth_IdAndTradeDate(planMonth.getId(), tradeDate)
                    .orElseThrow(() -> new IllegalStateException("No trading day row found for trade date " + tradeDate + "."));

            PnlDailyFact dailyFact = pnlDailyFactRepository.findByTradingDay_Id(tradingDay.getId())
                    .orElseGet(PnlDailyFact::new);

            dailyFact.setTradingDay(tradingDay);
            dailyFact.setSource(importSource);
            dailyFact.setPayInPayOut(scale(amount(obligation.getPayInPayOut())));
            dailyFact.setBrokerage(scale(amount(obligation.getBrokerage())));
            dailyFact.setTransactionCharges(scale(amount(obligation.getTransactionCharges())));
            dailyFact.setNetPnl(scale(amount(obligation.getNetAmount())));
            dailyFact.setNoOfTrades(numberOfTrades);
            dailyFact.setExtractedAt(LocalDateTime.now());
            pnlDailyFactRepository.save(dailyFact);

            importSource.setStatus("PROCESSED");
            importSource.setProcessedAt(LocalDateTime.now());
            pnlImportSourceRepository.save(importSource);

            return buildProcessResult(user, tradeDate, importSource, false, planType);
        } catch (RuntimeException ex) {
            importSource.setStatus("FAILED");
            importSource.setErrorMessage(ex.getMessage());
            importSource.setProcessedAt(LocalDateTime.now());
            pnlImportSourceRepository.save(importSource);
            throw ex;
        }
    }

    @Override
    public PnlPlanDto savePlan(UserDetails user, PnlPlanRequestDto request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User is required.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Plan request is required.");
        }
        if (request.getPlanName() == null || request.getPlanName().isBlank()) {
            throw new IllegalArgumentException("Plan name is required.");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Plan start and end dates are required.");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Plan end date must be on or after start date.");
        }
        if (request.getAnnualTarget() == null || request.getAnnualTarget().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Annual target must be zero or positive.");
        }

        PnlPlan plan;
        if (request.getId() != null) {
            plan = pnlPlanRepository.findByIdAndUser_Id(request.getId(), user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Plan not found."));
        } else {
            plan = new PnlPlan();
            plan.setUser(user);
            plan.setCreatedAt(LocalDateTime.now());
        }

        boolean active = request.getActive() == null || request.getActive();
        if (active) {
            deactivateOtherPlans(user.getId(), plan.getId(), plan.getPlanType());
        }

        plan.setUser(user);
        plan.setPlanName(request.getPlanName().trim());
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setAnnualTarget(scale(request.getAnnualTarget()));
        plan.setCurrency(normalizeCurrency(request.getCurrency()));
        plan.setActive(active);
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setPlanType(request.getPlanType() != null && !request.getPlanType().isBlank() ? request.getPlanType() : "FNO");
        plan.setStartingCapital(scale(request.getStartingCapital()));
        plan.setTotalAchievedAmount(ZERO);
        plan = pnlPlanRepository.save(plan);

        deactivateOtherPlans(user.getId(), plan.getId(), plan.getPlanType());

        ensurePlanMonths(plan);
        recalculateMonthlyTargetsForPlan(plan, determineTargetAllocationDate(plan));
        provisionInitialTradingStructure(plan);

        return toPlanDto(plan, pnlPlanMonthRepository.findByPlan_IdOrderByMonthSequenceAsc(plan.getId()));
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<PnlPlanDto> getPlans(UserDetails user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User is required.");
        }

        List<PnlPlan> plans = pnlPlanRepository.findByUser_IdOrderByStartDateDesc(user.getId());
        List<PnlPlanMonth> allMonths = pnlPlanMonthRepository.findByPlan_User_IdOrderByPlan_IdAscMonthSequenceAsc(user.getId());
        Map<Long, List<PnlPlanMonth>> monthsByPlanId = new LinkedHashMap<>();
        for (PnlPlanMonth month : allMonths) {
            monthsByPlanId.computeIfAbsent(month.getPlan().getId(), ignored -> new ArrayList<>()).add(month);
        }

        List<PnlPlanDto> planDtos = new ArrayList<>();
        for (PnlPlan plan : plans) {
            planDtos.add(toPlanDto(plan, monthsByPlanId.getOrDefault(plan.getId(), List.of())));
        }
        return planDtos;
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public PnlPlanDto getActivePlan(UserDetails user, LocalDate tradeDate, String planType) {
        PnlPlan plan = resolveActivePlan(user, defaultTradeDate(tradeDate), planType);
        return toPlanDto(plan, pnlPlanMonthRepository.findByPlan_IdOrderByMonthSequenceAsc(plan.getId()));
    }

    @Override
    public PnlPlanDto updateMonthTarget(UserDetails user, Long monthId, PnlMonthTargetUpdateDto request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User is required.");
        }
        if (monthId == null) {
            throw new IllegalArgumentException("Month id is required.");
        }

        PnlPlanMonth month = pnlPlanMonthRepository.findByIdAndPlan_User_Id(monthId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Plan month not found."));

        month.setManualTarget(request != null && request.getManualTarget() != null ? scale(request.getManualTarget()) : null);
        month.setUpdatedAt(LocalDateTime.now());
        pnlPlanMonthRepository.save(month);

        PnlPlan plan = month.getPlan();
        recalculateMonthlyTargetsForPlan(plan, determineTargetAllocationDate(plan));
        syncStoredDailyTargets(plan, month);
        return toPlanDto(plan, pnlPlanMonthRepository.findByPlan_IdOrderByMonthSequenceAsc(plan.getId()));
    }

    @Override
    public PnlProcessResultDto upsertManualDailyPnl(UserDetails user, PnlManualEntryRequestDto request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User is required.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Manual daily PnL request is required.");
        }
        if (request.getSelectedPlan() == null) {
            throw new IllegalArgumentException("Selected plan is required.");
        }
        if (request.getPnlAmount() == null) {
            throw new IllegalArgumentException("PnL amount is required.");
        }

        LocalDate effectiveTradeDate = defaultTradeDate(request.getTradeDate());
        PnlPlan plan = pnlPlanRepository.findByIdAndUser_Id(request.getSelectedPlan(), user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Selected plan not found."));
        if (effectiveTradeDate.isBefore(plan.getStartDate()) || effectiveTradeDate.isAfter(plan.getEndDate())) {
            throw new IllegalArgumentException("Trade date is outside the selected plan period.");
        }
        PnlPlanMonth planMonth = findPlanMonthOrCreate(plan, effectiveTradeDate);
        ensureTradingDaysForTradeDate(plan, planMonth, effectiveTradeDate);

        PnlTradingDay tradingDay = pnlTradingDayRepository.findByPlanMonth_IdAndTradeDate(planMonth.getId(), effectiveTradeDate)
                .orElseThrow(() -> new IllegalStateException("No trading day row found for trade date " + effectiveTradeDate + "."));

        PnlDailyFact dailyFact = pnlDailyFactRepository.findByTradingDay_Id(tradingDay.getId())
                .orElseGet(PnlDailyFact::new);

        dailyFact.setTradingDay(tradingDay);
        dailyFact.setSource(null);
        dailyFact.setPayInPayOut(null);
        dailyFact.setBrokerage(null);
        dailyFact.setTransactionCharges(request.getTransactionCharges() != null ? scale(request.getTransactionCharges()) : null);
        dailyFact.setNetPnl(scale(request.getPnlAmount()));
        dailyFact.setNoOfTrades(null);
        dailyFact.setExtractedAt(LocalDateTime.now());
        pnlDailyFactRepository.save(dailyFact);

        tradingDay.setRemark(normalizeRemark(request.getRemarks()));
        tradingDay.setUpdatedAt(LocalDateTime.now());
        pnlTradingDayRepository.save(tradingDay);

        plan.setTotalAchievedAmount(pnlDailyFactRepository.sumNetPnlByPlanId(plan.getId()));
        pnlPlanRepository.save(plan);

        syncStoredDailyTargets(plan, planMonth);
        return buildManualProcessResult(plan, planMonth, effectiveTradeDate);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public PnlWorkbookViewDto getWorkbookView(UserDetails user, LocalDate tradeDate, String planType) {
        LocalDate effectiveTradeDate = defaultTradeDate(tradeDate);
        PnlPlan plan = resolveActivePlan(user, effectiveTradeDate, planType);
        List<PnlPlanMonth> months = pnlPlanMonthRepository.findByPlan_IdOrderByMonthSequenceAsc(plan.getId());
        PnlPlanMonth currentMonth = findPlanMonth(plan.getId(), effectiveTradeDate);
        List<PnlMonthSummaryDto> yearSummary = buildYearSummary(plan, months, groupTradingDaysByMonth(plan.getId(), months));

        PnlWorkbookViewDto workbookView = new PnlWorkbookViewDto();
        workbookView.setPlan(toPlanDto(plan, months));
        workbookView.setMonthLabel(currentMonth.getMonthLabel());
        workbookView.setTradeDate(effectiveTradeDate);
        workbookView.setCurrentMonthSummary(
                yearSummary.stream()
                        .filter(summary -> summary.getMonthSequence().equals(currentMonth.getMonthSequence()))
                        .findFirst()
                        .orElse(null)
        );
        return workbookView;
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public PnlMonthSummaryDto getCurrentMonthSummary(UserDetails user, LocalDate tradeDate, String planType) {
        LocalDate effectiveTradeDate = defaultTradeDate(tradeDate);
        PnlPlan plan = resolveActivePlan(user, effectiveTradeDate, planType);
        List<PnlPlanMonth> months = pnlPlanMonthRepository.findByPlan_IdOrderByMonthSequenceAsc(plan.getId());
        PnlPlanMonth currentMonth = findPlanMonth(plan.getId(), effectiveTradeDate);
        List<PnlMonthSummaryDto> yearSummary = buildYearSummary(plan, months, groupTradingDaysByMonth(plan.getId(), months));

        return yearSummary.stream()
                .filter(summary -> summary.getMonthSequence().equals(currentMonth.getMonthSequence()))
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<PnlMonthSummaryDto> getYearSummary(UserDetails user, LocalDate tradeDate, String planType) {
        LocalDate effectiveTradeDate = defaultTradeDate(tradeDate);
        PnlPlan plan = resolveActivePlan(user, effectiveTradeDate, planType);
        List<PnlPlanMonth> months = pnlPlanMonthRepository.findByPlan_IdOrderByMonthSequenceAsc(plan.getId());
        return buildYearSummary(plan, months, groupTradingDaysByMonth(plan.getId(), months));
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<PnlDailyCalculationDto> getMonthSheet(UserDetails user, LocalDate tradeDate, String planType) {
        LocalDate effectiveTradeDate = defaultTradeDate(tradeDate);
        PnlPlan plan = resolveActivePlan(user, effectiveTradeDate, planType);
        PnlPlanMonth currentMonth = findPlanMonth(plan.getId(), effectiveTradeDate);

        ensureTradingDaysForTradeDate(plan, currentMonth, effectiveTradeDate);

        List<PnlPlanMonth> months = pnlPlanMonthRepository.findByPlan_IdOrderByMonthSequenceAsc(plan.getId());
        Map<Long, List<PnlTradingDay>> monthTradingDays = groupTradingDaysByMonth(plan.getId(), months);
        List<PnlMonthSummaryDto> yearSummary = buildYearSummary(plan, months, monthTradingDays);

        return buildDailySheet(currentMonth, monthTradingDays.getOrDefault(currentMonth.getId(), List.of()), yearSummary);
    }

    @Override
    public void generateMonthlyStructure(LocalDate tradeDate) {
        LocalDate effectiveTradeDate = defaultTradeDate(tradeDate);
        List<PnlPlan> activePlans = pnlPlanRepository
                .findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(effectiveTradeDate, effectiveTradeDate);

        for (PnlPlan plan : activePlans) {
            ensurePlanMonths(plan);
            generateMonthlyStructureForPlan(plan, effectiveTradeDate);
        }
    }

    @Override
    public void ensureMonthlyStructure(UserDetails user, LocalDate tradeDate, String planType) {
        LocalDate effectiveTradeDate = defaultTradeDate(tradeDate);
        PnlPlan plan = resolveActivePlan(user, effectiveTradeDate, planType);
        ensurePlanMonths(plan);
        generateMonthlyStructureForPlan(plan, effectiveTradeDate);
    }

    @Override
    public void recalculateMonthlyTargets(LocalDate tradeDate) {
        LocalDate effectiveTradeDate = defaultTradeDate(tradeDate);
        List<PnlPlan> activePlans = pnlPlanRepository
                .findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(effectiveTradeDate, effectiveTradeDate);

        for (PnlPlan plan : activePlans) {
            ensurePlanMonths(plan);
            recalculateMonthlyTargetsForPlan(plan, effectiveTradeDate);
        }
    }

    private Optional<PnlImportSource> findProcessedDuplicate(
            Long userId,
            LocalDate tradeDate,
            String gmailMessageId,
            String attachmentChecksum
    ) {
        Optional<PnlImportSource> byMessage = findByMessage(userId, gmailMessageId);
        if (byMessage.isPresent() && "PROCESSED".equalsIgnoreCase(byMessage.get().getStatus())) {
            return byMessage;
        }

        Optional<PnlImportSource> byChecksum = findByChecksum(userId, tradeDate, attachmentChecksum);
        if (byChecksum.isPresent() && "PROCESSED".equalsIgnoreCase(byChecksum.get().getStatus())) {
            return byChecksum;
        }

        return Optional.empty();
    }

    private void deactivateOtherPlans(Long userId, Long currentPlanId, String planType) {
        List<PnlPlan> plans = pnlPlanRepository.findByUser_IdOrderByStartDateDesc(userId);
        for (PnlPlan existingPlan : plans) {
            if (currentPlanId != null && currentPlanId.equals(existingPlan.getId())) {
                continue;
            }
            if (existingPlan.isActive() && planType.equals(existingPlan.getPlanType())) {
                existingPlan.setActive(false);
                existingPlan.setUpdatedAt(LocalDateTime.now());
            }
        }
        pnlPlanRepository.saveAll(plans);
    }

    private Optional<PnlImportSource> findReusableImport(
            Long userId,
            LocalDate tradeDate,
            String gmailMessageId,
            String attachmentChecksum
    ) {
        Optional<PnlImportSource> byMessage = findByMessage(userId, gmailMessageId);
        if (byMessage.isPresent() && !"PROCESSED".equalsIgnoreCase(byMessage.get().getStatus())) {
            return byMessage;
        }

        Optional<PnlImportSource> byChecksum = findByChecksum(userId, tradeDate, attachmentChecksum);
        if (byChecksum.isPresent() && !"PROCESSED".equalsIgnoreCase(byChecksum.get().getStatus())) {
            return byChecksum;
        }

        return Optional.empty();
    }

    private Optional<PnlImportSource> findByMessage(Long userId, String gmailMessageId) {
        if (gmailMessageId == null || gmailMessageId.isBlank()) {
            return Optional.empty();
        }
        return pnlImportSourceRepository.findByUser_IdAndGmailMessageId(userId, gmailMessageId);
    }

    private Optional<PnlImportSource> findByChecksum(Long userId, LocalDate tradeDate, String attachmentChecksum) {
        if (attachmentChecksum == null || attachmentChecksum.isBlank()) {
            return Optional.empty();
        }
        return pnlImportSourceRepository.findByUser_IdAndTradeDateAndAttachmentChecksum(userId, tradeDate, attachmentChecksum);
    }

    private void ensurePlanMonths(PnlPlan plan) {
        List<PnlPlanMonth> existingMonths = pnlPlanMonthRepository.findByPlan_IdOrderByMonthSequenceAsc(plan.getId());
        Map<LocalDate, PnlPlanMonth> existingByStartDate = new HashMap<>();
        for (PnlPlanMonth month : existingMonths) {
            existingByStartDate.put(month.getStartDate(), month);
        }

        List<PnlPlanMonth> monthsToSave = new ArrayList<>();
        LocalDate cursor = plan.getStartDate().withDayOfMonth(1);
        int sequence = 1;

        while (!cursor.isAfter(plan.getEndDate())) {
            LocalDate monthStart = max(plan.getStartDate(), cursor);
            LocalDate monthEnd = min(plan.getEndDate(), cursor.with(TemporalAdjusters.lastDayOfMonth()));
            PnlPlanMonth month = existingByStartDate.getOrDefault(monthStart, new PnlPlanMonth());
            boolean changed = month.getId() == null;
            int tradingDaysPlanned = Math.max(1, countTradingDays(monthStart, monthEnd));
            String monthLabel = cursor.format(MONTH_LABEL_FORMATTER).toUpperCase(Locale.ENGLISH);

            if (month.getPlan() == null || !plan.getId().equals(month.getPlan().getId())) {
                month.setPlan(plan);
                changed = true;
            }
            if (!Integer.valueOf(sequence).equals(month.getMonthSequence())) {
                month.setMonthSequence(sequence);
                changed = true;
            }
            if (!monthLabel.equals(month.getMonthLabel())) {
                month.setMonthLabel(monthLabel);
                changed = true;
            }
            if (!monthStart.equals(month.getStartDate())) {
                month.setStartDate(monthStart);
                changed = true;
            }
            if (!monthEnd.equals(month.getEndDate())) {
                month.setEndDate(monthEnd);
                changed = true;
            }
            if (!Integer.valueOf(tradingDaysPlanned).equals(month.getTradingDaysPlanned())) {
                month.setTradingDaysPlanned(tradingDaysPlanned);
                changed = true;
            }

            if (month.getCreatedAt() == null) {
                month.setCreatedAt(LocalDateTime.now());
                changed = true;
            }

            if (changed) {
                month.setUpdatedAt(LocalDateTime.now());
                monthsToSave.add(month);
            }
            cursor = cursor.plusMonths(1);
            sequence++;
        }

        if (!monthsToSave.isEmpty()) {
            pnlPlanMonthRepository.saveAll(monthsToSave);
        }
    }

    private void generateMonthlyStructureForPlan(PnlPlan plan, LocalDate tradeDate) {
        if (tradeDate.isBefore(plan.getStartDate()) || tradeDate.isAfter(plan.getEndDate())) {
            return;
        }

        PnlPlanMonth month = pnlPlanMonthRepository
                .findFirstByPlan_IdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(plan.getId(), tradeDate, tradeDate)
                .orElseThrow(() -> new IllegalStateException("No plan month found for trade date " + tradeDate + "."));

        ensureTradingDaysForTradeDate(plan, month, tradeDate);
        syncStoredDailyTargets(plan, month);
    }

    private void syncStoredDailyTargets(PnlPlan plan, PnlPlanMonth currentMonth) {
        List<PnlTradingDay> tradingDays = pnlTradingDayRepository
                .findWithDailyFactByPlanMonth_IdOrderByTradingDaySeqAsc(currentMonth.getId());
        if (tradingDays.isEmpty()) {
            return;
        }

        persistDailyPlans(currentMonth, tradingDays, effectiveStoredMonthTarget(currentMonth));
    }

    private void recalculateMonthlyTargetsForPlan(PnlPlan plan, LocalDate allocationDate) {
        LocalDate effectiveDate = allocationDate;
        if (effectiveDate.isBefore(plan.getStartDate())) {
            effectiveDate = plan.getStartDate();
        }
        if (effectiveDate.isAfter(plan.getEndDate())) {
            return;
        }

        List<PnlPlanMonth> months = pnlPlanMonthRepository.findByPlan_IdOrderByMonthSequenceAsc(plan.getId());
        if (months.isEmpty()) {
            return;
        }

        BigDecimal achievedBeforeAllocation = scale(pnlDailyFactRepository.sumNetPnlByPlanIdBeforeDate(plan.getId(), effectiveDate));
        BigDecimal remainingAnnualBalance = scale(scale(plan.getAnnualTarget()).subtract(achievedBeforeAllocation));

        List<PnlPlanMonth> monthsToSave = new ArrayList<>();
        List<PnlPlanMonth> variableMonths = new ArrayList<>();
        Map<Long, Integer> monthWeights = new HashMap<>();
        BigDecimal fixedTargets = ZERO;
        int totalVariableWeight = 0;

        for (PnlPlanMonth month : months) {
            if (month.getEndDate().isBefore(effectiveDate)) {
                continue;
            }

            LocalDate weightStartDate = max(month.getStartDate(), effectiveDate);
            int tradingWeight = countTradingDays(weightStartDate, month.getEndDate());
            monthWeights.put(month.getId(), tradingWeight);

            if (month.getManualTarget() != null) {
                fixedTargets = fixedTargets.add(scale(month.getManualTarget()));
            } else if (tradingWeight > 0) {
                variableMonths.add(month);
                totalVariableWeight += tradingWeight;
            }
        }

        BigDecimal remainingForVariableMonths = scale(remainingAnnualBalance.subtract(fixedTargets));
        BigDecimal allocatedSoFar = ZERO;
        PnlPlanMonth lastVariableMonth = variableMonths.isEmpty() ? null : variableMonths.get(variableMonths.size() - 1);

        for (PnlPlanMonth month : months) {
            if (month.getEndDate().isBefore(effectiveDate)) {
                continue;
            }

            BigDecimal newAllocatedTarget;
            if (month.getManualTarget() != null) {
                newAllocatedTarget = scale(month.getManualTarget());
            } else {
                int tradingWeight = monthWeights.getOrDefault(month.getId(), 0);
                if (tradingWeight <= 0 || totalVariableWeight == 0) {
                    newAllocatedTarget = ZERO;
                } else if (lastVariableMonth != null && month.getId().equals(lastVariableMonth.getId())) {
                    newAllocatedTarget = scale(remainingForVariableMonths.subtract(allocatedSoFar));
                } else {
                    newAllocatedTarget = scale(
                            remainingForVariableMonths
                                    .multiply(BigDecimal.valueOf(tradingWeight))
                                    .divide(BigDecimal.valueOf(totalVariableWeight), 2, RoundingMode.HALF_UP)
                    );
                    allocatedSoFar = allocatedSoFar.add(newAllocatedTarget);
                }
            }

            if (!sameAmount(month.getAllocatedTarget(), newAllocatedTarget) || month.getTargetCalculatedAt() == null) {
                month.setAllocatedTarget(newAllocatedTarget);
                month.setTargetCalculatedAt(LocalDateTime.now());
                month.setUpdatedAt(LocalDateTime.now());
                monthsToSave.add(month);
            }
        }

        if (!monthsToSave.isEmpty()) {
            pnlPlanMonthRepository.saveAll(monthsToSave);
        }
    }

    private void provisionInitialTradingStructure(PnlPlan plan) {
        LocalDate today = LocalDate.now();
        if (plan.getEndDate().isBefore(today)) {
            LocalDate previousMonthDate = today.minusMonths(1).withDayOfMonth(1);
            if (!previousMonthDate.isBefore(plan.getStartDate()) && !previousMonthDate.isAfter(plan.getEndDate())) {
                generateMonthlyStructureForPlan(plan, previousMonthDate);
            }
            return;
        }

        if (plan.getStartDate().isBefore(today.withDayOfMonth(1))) {
            LocalDate previousMonthDate = today.minusMonths(1).withDayOfMonth(1);
            if (!previousMonthDate.isBefore(plan.getStartDate()) && !previousMonthDate.isAfter(plan.getEndDate())) {
                generatePreviousMonthStructure(plan, previousMonthDate);
            }
        }

        LocalDate seedDate = determineSeedDate(plan);
        generateMonthlyStructureForPlan(plan, seedDate);
    }

    private void generatePreviousMonthStructure(PnlPlan plan, LocalDate anyDateInPreviousMonth) {
        PnlPlanMonth previousMonth = pnlPlanMonthRepository
                .findFirstByPlan_IdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        plan.getId(),
                        anyDateInPreviousMonth,
                        anyDateInPreviousMonth
                )
                .orElse(null);
        if (previousMonth == null) {
            return;
        }
        ensureTradingDaysInRange(previousMonth, previousMonth.getStartDate(), previousMonth.getEndDate());
        syncStoredDailyTargets(plan, previousMonth);
    }

    private void ensureTradingDaysForTradeDate(PnlPlan plan, PnlPlanMonth planMonth, LocalDate tradeDate) {
        LocalDate today = LocalDate.now();
        if (planMonth.getEndDate().isBefore(today.withDayOfMonth(1))) {
            ensureTradingDaysInRange(planMonth, planMonth.getStartDate(), planMonth.getEndDate());
            return;
        }

        LocalDate anchorDate = resolveCurrentWindowEndDate(planMonth, tradeDate);
        ensureTradingDaysInRange(planMonth, planMonth.getStartDate(), anchorDate);
    }

    private LocalDate resolveCurrentWindowEndDate(PnlPlanMonth planMonth, LocalDate tradeDate) {
        LocalDate today = LocalDate.now();
        LocalDate referenceDate = tradeDate;
        if (isSameMonth(planMonth.getStartDate(), today)) {
            referenceDate = tradeDate.isBefore(today) ? today : tradeDate;
        }

        LocalDate windowStart = max(planMonth.getStartDate(), referenceDate);
        List<LocalDate> upcomingTradingDates = resolveTradingDates(windowStart, planMonth.getEndDate());
        if (upcomingTradingDates.isEmpty()) {
            return min(planMonth.getEndDate(), referenceDate);
        }
        if (upcomingTradingDates.size() == 1) {
            return upcomingTradingDates.get(0);
        }
        return upcomingTradingDates.get(1);
    }

    private void ensureTradingDaysInRange(PnlPlanMonth planMonth, LocalDate fromDate, LocalDate toDate) {
        LocalDate effectiveFrom = max(planMonth.getStartDate(), fromDate);
        LocalDate effectiveTo = min(planMonth.getEndDate(), toDate);
        if (effectiveFrom.isAfter(effectiveTo)) {
            return;
        }

        List<LocalDate> fullMonthTradingDates = resolveTradingDates(planMonth.getStartDate(), planMonth.getEndDate());
        List<LocalDate> tradingDates = new ArrayList<>();
        for (LocalDate tradeDate : fullMonthTradingDates) {
            if (!tradeDate.isBefore(effectiveFrom) && !tradeDate.isAfter(effectiveTo)) {
                tradingDates.add(tradeDate);
            }
        }
        if (tradingDates.isEmpty()) {
            return;
        }

        List<PnlTradingDay> existingDays = pnlTradingDayRepository.findByPlanMonth_IdOrderByTradingDaySeqAsc(planMonth.getId());
        Map<LocalDate, PnlTradingDay> existingByDate = new HashMap<>();
        for (PnlTradingDay day : existingDays) {
            existingByDate.put(day.getTradeDate(), day);
        }

        List<PnlTradingDay> daysToSave = new ArrayList<>();
        for (LocalDate tradeDate : tradingDates) {
            PnlTradingDay tradingDay = existingByDate.getOrDefault(tradeDate, new PnlTradingDay());
            boolean changed = tradingDay.getId() == null;
            int tradingDaySequence = fullMonthTradingDates.indexOf(tradeDate) + 1;

            if (tradingDay.getPlanMonth() == null || !planMonth.getId().equals(tradingDay.getPlanMonth().getId())) {
                tradingDay.setPlanMonth(planMonth);
                changed = true;
            }
            if (!tradeDate.equals(tradingDay.getTradeDate())) {
                tradingDay.setTradeDate(tradeDate);
                changed = true;
            }
            if (!Integer.valueOf(tradingDaySequence).equals(tradingDay.getTradingDaySeq())) {
                tradingDay.setTradingDaySeq(tradingDaySequence);
                changed = true;
            }
            if (tradingDay.getCreatedAt() == null) {
                tradingDay.setCreatedAt(LocalDateTime.now());
                changed = true;
            }
            if (changed) {
                tradingDay.setUpdatedAt(LocalDateTime.now());
                daysToSave.add(tradingDay);
            }
        }

        if (!daysToSave.isEmpty()) {
            pnlTradingDayRepository.saveAll(daysToSave);
        }
    }

    private List<LocalDate> resolveTradingDates(LocalDate startDate, LocalDate endDate) {
        List<TradingCalendar> calendarDates = tradingCalendarRepository
                .findByTradeDateBetweenAndIsTradingDayTrueOrderByTradeDateAsc(startDate, endDate);

        if (!calendarDates.isEmpty()) {
            List<LocalDate> dates = new ArrayList<>();
            for (TradingCalendar calendarDate : calendarDates) {
                dates.add(calendarDate.getTradeDate());
            }
            return dates;
        }

        List<LocalDate> weekdayDates = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            DayOfWeek dayOfWeek = cursor.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                weekdayDates.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return weekdayDates;
    }

    private int countTradingDays(LocalDate startDate, LocalDate endDate) {
        return resolveTradingDates(startDate, endDate).size();
    }

    private PnlProcessResultDto buildProcessResult(UserDetails user, LocalDate tradeDate, PnlImportSource importSource, boolean duplicate, String planType) {
        PnlPlan plan = resolveActivePlan(user, tradeDate, planType);

        PnlPlanMonth currentMonth = findPlanMonth(plan.getId(), tradeDate);
        ensureTradingDaysForTradeDate(plan, currentMonth, tradeDate);
        syncStoredDailyTargets(plan, currentMonth);

        List<PnlPlanMonth> months = pnlPlanMonthRepository.findByPlan_IdOrderByMonthSequenceAsc(plan.getId());
        Map<Long, List<PnlTradingDay>> monthTradingDays = groupTradingDaysByMonth(plan.getId(), months);

        List<PnlMonthSummaryDto> yearSummary = buildYearSummary(plan, months, monthTradingDays);
        List<PnlDailyCalculationDto> dailySheet = buildDailySheet(currentMonth, monthTradingDays.getOrDefault(currentMonth.getId(), List.of()), yearSummary);

        PnlMonthSummaryDto currentMonthSummary = yearSummary.stream()
                .filter(summary -> summary.getMonthSequence().equals(currentMonth.getMonthSequence()))
                .findFirst()
                .orElse(null);

        PnlProcessResultDto result = new PnlProcessResultDto();
        result.setPersisted(true);
        result.setDuplicateImport(duplicate);
        result.setPlanName(plan.getPlanName());
        result.setMonthLabel(currentMonth.getMonthLabel());
        result.setTradeDate(tradeDate);
        result.setImportSourceId(importSource.getId());
        result.setCurrentMonthSummary(currentMonthSummary);
        result.setYearSummary(yearSummary);
        result.setDailySheet(dailySheet);
        return result;
    }

    private PnlProcessResultDto buildManualProcessResult(PnlPlan plan, PnlPlanMonth currentMonth, LocalDate tradeDate) {
        List<PnlTradingDay> currentMonthTradingDays = pnlTradingDayRepository
                .findWithDailyFactByPlanMonth_IdOrderByTradingDaySeqAsc(currentMonth.getId());

        BigDecimal monthTarget = effectiveStoredMonthTarget(currentMonth);
        BigDecimal monthAchieved = calculateMonthAchieved(currentMonthTradingDays);
        BigDecimal achievedBeforeMonth = scale(
                pnlDailyFactRepository.sumNetPnlByPlanIdBeforeDate(plan.getId(), currentMonth.getStartDate())
        );

        PnlMonthSummaryDto currentMonthSummary = new PnlMonthSummaryDto();
        currentMonthSummary.setMonthSequence(currentMonth.getMonthSequence());
        currentMonthSummary.setMonthLabel(currentMonth.getMonthLabel());
        currentMonthSummary.setTradingDaysPlanned(currentMonth.getTradingDaysPlanned());
        currentMonthSummary.setMonthTarget(monthTarget);
        currentMonthSummary.setMonthAchieved(monthAchieved);
        currentMonthSummary.setMonthBalance(scale(monthTarget.subtract(monthAchieved)));
        currentMonthSummary.setMonthAchievedPct(percentage(monthAchieved, monthTarget));
        currentMonthSummary.setYtdAchieved(scale(achievedBeforeMonth.add(monthAchieved)));
        currentMonthSummary.setYtdBalance(scale(scale(plan.getAnnualTarget()).subtract(currentMonthSummary.getYtdAchieved())));

        List<PnlDailyCalculationDto> dailySheet = buildDailySheet(currentMonth, currentMonthTradingDays, monthTarget);

        PnlProcessResultDto result = new PnlProcessResultDto();
        result.setPersisted(true);
        result.setDuplicateImport(false);
        result.setPlanName(plan.getPlanName());
        result.setMonthLabel(currentMonth.getMonthLabel());
        result.setTradeDate(tradeDate);
        result.setImportSourceId(null);
        result.setCurrentMonthSummary(currentMonthSummary);
        result.setYearSummary(null);
        result.setDailySheet(dailySheet);
        return result;
    }

    private PnlPlan resolveActivePlan(UserDetails user, LocalDate tradeDate, String providedType) {
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
                .orElseThrow(() -> new IllegalStateException("No active P&L plan found for trade date " + tradeDate + " and plan type " + planType + "."));
    }

    private PnlPlanMonth findPlanMonth(Long planId, LocalDate tradeDate) {
        return pnlPlanMonthRepository
                .findFirstByPlan_IdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(planId, tradeDate, tradeDate)
                .orElseThrow(() -> new IllegalStateException("No plan month found for trade date " + tradeDate + "."));
    }

    private PnlPlanMonth findPlanMonthOrCreate(PnlPlan plan, LocalDate tradeDate) {
        try {
            return findPlanMonth(plan.getId(), tradeDate);
        } catch (IllegalStateException ex) {
            ensurePlanMonths(plan);
            return findPlanMonth(plan.getId(), tradeDate);
        }
    }

    private Map<Long, List<PnlTradingDay>> groupTradingDaysByMonth(Long planId, List<PnlPlanMonth> months) {
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

    private LocalDate determineSeedDate(PnlPlan plan) {
        LocalDate today = LocalDate.now();
        if (!today.isBefore(plan.getStartDate()) && !today.isAfter(plan.getEndDate())) {
            return today;
        }
        return plan.getStartDate();
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

        tradingDays = new ArrayList<>(tradingDays);
        tradingDays.sort(Comparator.comparing(PnlTradingDay::getTradingDaySeq));

        PnlMonthSummaryDto currentMonthSummary = yearSummary.stream()
                .filter(summary -> summary.getMonthSequence().equals(currentMonth.getMonthSequence()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Current month summary could not be calculated."));

        BigDecimal achievedBeforeDay = ZERO;
        BigDecimal achievedToDate = ZERO;
        int pendingTradingDays = 0;
        List<PnlDailyCalculationDto> dailySheet = new ArrayList<>();

        for (PnlTradingDay tradingDay : tradingDays) {
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

    private List<PnlDailyCalculationDto> buildDailySheet(
            PnlPlanMonth currentMonth,
            List<PnlTradingDay> tradingDays,
            BigDecimal monthTarget
    ) {
        if (tradingDays == null) {
            return List.of();
        }

        List<PnlTradingDay> sortedTradingDays = new ArrayList<>(tradingDays);
        sortedTradingDays.sort(Comparator.comparing(PnlTradingDay::getTradingDaySeq));

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
                        : historicalDailyPlan(monthTarget, currentMonth.getTradingDaysPlanned(), tradingDay.getTradingDaySeq(), achievedBeforeDay);
                dailyCalculation.setDailyPlan(dailyPlan);
                BigDecimal mtdAchieved = scale(achievedBeforeDay.add(actualPnl));
                dailyCalculation.setMtdAchieved(mtdAchieved);
                dailyCalculation.setYtaMonth(scale(monthTarget.subtract(mtdAchieved)));
                dailyCalculation.setMtdPct(percentage(mtdAchieved, monthTarget));
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
                scale(monthTarget.subtract(achievedToDate)),
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

    private void persistDailyPlans(
            PnlPlanMonth currentMonth,
            List<PnlTradingDay> tradingDays,
            BigDecimal monthTarget
    ) {
        if (tradingDays == null || tradingDays.isEmpty()) {
            return;
        }

        List<PnlTradingDay> sortedTradingDays = new ArrayList<>(tradingDays);
        sortedTradingDays.sort(Comparator.comparing(PnlTradingDay::getTradingDaySeq));

        BigDecimal achievedBeforeDay = ZERO;
        BigDecimal achievedToDate = ZERO;
        List<PnlTradingDay> pendingDays = new ArrayList<>();
        List<PnlTradingDay> daysToSave = new ArrayList<>();

        for (PnlTradingDay tradingDay : sortedTradingDays) {
            BigDecimal actualPnl = tradingDay.getDailyFact() != null ? scale(tradingDay.getDailyFact().getNetPnl()) : null;
            if (actualPnl != null) {
                BigDecimal dailyPlan = historicalDailyPlan(
                        monthTarget,
                        currentMonth.getTradingDaysPlanned(),
                        tradingDay.getTradingDaySeq(),
                        achievedBeforeDay
                );
                markDailyTargetIfChanged(tradingDay, dailyPlan, daysToSave);
                achievedBeforeDay = scale(achievedBeforeDay.add(actualPnl));
                achievedToDate = achievedBeforeDay;
            } else {
                pendingDays.add(tradingDay);
            }
        }

        BigDecimal pendingDailyPlan = divide(
                scale(monthTarget.subtract(achievedToDate)),
                pendingDays.size(),
                2
        );
        for (PnlTradingDay pendingDay : pendingDays) {
            markDailyTargetIfChanged(pendingDay, pendingDailyPlan, daysToSave);
        }

        if (!daysToSave.isEmpty()) {
            pnlTradingDayRepository.saveAll(daysToSave);
        }
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

    private void markDailyTargetIfChanged(PnlTradingDay tradingDay, BigDecimal dailyTarget, List<PnlTradingDay> daysToSave) {
        BigDecimal scaledDailyTarget = dailyTarget != null ? scale(dailyTarget) : null;
        if (!sameAmount(tradingDay.getDailyTarget(), scaledDailyTarget) || tradingDay.getDailyTargetUpdatedAt() == null) {
            tradingDay.setDailyTarget(scaledDailyTarget);
            tradingDay.setDailyTargetUpdatedAt(LocalDateTime.now());
            tradingDay.setUpdatedAt(LocalDateTime.now());
            daysToSave.add(tradingDay);
        }
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
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

    private BigDecimal amount(double value) {
        return BigDecimal.valueOf(value);
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

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "INR";
        }
        return currency.trim().toUpperCase(Locale.ENGLISH);
    }

    private String normalizeRemark(String remark) {
        if (remark == null || remark.isBlank()) {
            return null;
        }
        return remark.trim();
    }

    private LocalDate defaultTradeDate(LocalDate tradeDate) {
        return tradeDate != null ? tradeDate : LocalDate.now();
    }

    private LocalDate determineTargetAllocationDate(PnlPlan plan) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(plan.getStartDate())) {
            return plan.getStartDate();
        }
        if (today.isAfter(plan.getEndDate())) {
            return plan.getEndDate();
        }
        return today;
    }

    private LocalDate max(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private LocalDate min(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }

    private boolean isSameMonth(LocalDate first, LocalDate second) {
        return first.getYear() == second.getYear() && first.getMonth() == second.getMonth();
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

    private BigDecimal effectiveStoredMonthTarget(PnlPlanMonth month) {
        if (month.getAllocatedTarget() != null) {
            return scale(month.getAllocatedTarget());
        }
        if (month.getManualTarget() != null) {
            return scale(month.getManualTarget());
        }
        return ZERO;
    }
}

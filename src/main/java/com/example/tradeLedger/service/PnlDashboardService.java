package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.PnlDailyCalculationDto;
import com.example.tradeLedger.dto.PnlMonthSummaryDto;
import com.example.tradeLedger.dto.PnlWorkbookViewDto;
import com.example.tradeLedger.entity.UserDetails;

import java.time.LocalDate;
import java.util.List;

public interface PnlDashboardService {

    PnlWorkbookViewDto getWorkbookView(UserDetails user, LocalDate tradeDate, String planType);

    PnlMonthSummaryDto getCurrentMonthSummary(UserDetails user, LocalDate tradeDate, String planType);

    List<PnlMonthSummaryDto> getYearSummary(UserDetails user, LocalDate tradeDate, String planType);

    List<PnlDailyCalculationDto> getMonthSheet(UserDetails user, LocalDate tradeDate, String planType);
}

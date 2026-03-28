package com.example.tradeLedger.dto;

import java.util.List;

public class PnlPlanMonthDetailsDto {

    private PnlPlanDto plan;
    private Long monthId;
    private Integer monthSequence;
    private String monthLabel;
    private PnlMonthSummaryDto monthSummary;
    private List<PnlDailyCalculationDto> dailySheet;

    public PnlPlanDto getPlan() {
        return plan;
    }

    public void setPlan(PnlPlanDto plan) {
        this.plan = plan;
    }

    public Long getMonthId() {
        return monthId;
    }

    public void setMonthId(Long monthId) {
        this.monthId = monthId;
    }

    public Integer getMonthSequence() {
        return monthSequence;
    }

    public void setMonthSequence(Integer monthSequence) {
        this.monthSequence = monthSequence;
    }

    public String getMonthLabel() {
        return monthLabel;
    }

    public void setMonthLabel(String monthLabel) {
        this.monthLabel = monthLabel;
    }

    public PnlMonthSummaryDto getMonthSummary() {
        return monthSummary;
    }

    public void setMonthSummary(PnlMonthSummaryDto monthSummary) {
        this.monthSummary = monthSummary;
    }

    public List<PnlDailyCalculationDto> getDailySheet() {
        return dailySheet;
    }

    public void setDailySheet(List<PnlDailyCalculationDto> dailySheet) {
        this.dailySheet = dailySheet;
    }
}

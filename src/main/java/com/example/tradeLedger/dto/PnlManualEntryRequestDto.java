package com.example.tradeLedger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PnlManualEntryRequestDto {

    private Long selectedPlan;
    private LocalDate tradeDate;
    private BigDecimal pnlAmount;
    private BigDecimal transactionCharges;
    private String remarks;

    public Long getSelectedPlan() {
        return selectedPlan;
    }

    public void setSelectedPlan(Long selectedPlan) {
        this.selectedPlan = selectedPlan;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public BigDecimal getPnlAmount() {
        return pnlAmount;
    }

    public void setPnlAmount(BigDecimal pnlAmount) {
        this.pnlAmount = pnlAmount;
    }

    public BigDecimal getTransactionCharges() {
        return transactionCharges;
    }

    public void setTransactionCharges(BigDecimal transactionCharges) {
        this.transactionCharges = transactionCharges;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}

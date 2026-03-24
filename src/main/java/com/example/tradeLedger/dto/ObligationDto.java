package com.example.tradeLedger.dto;

import lombok.Data;

@Data
public class ObligationDto {

    private double payInPayOut;
    private double brokerage;
    private double netAmount;

    private double transactionCharges;

    public ObligationDto() {
    }

    public ObligationDto(double payInPayOut, double brokerage, double netAmount) {
        this.payInPayOut = payInPayOut;
        this.brokerage = brokerage;
        this.netAmount = netAmount;
    }

    public double getPayInPayOut() {
        return payInPayOut;
    }

    public void setPayInPayOut(double payInPayOut) {
        this.payInPayOut = payInPayOut;
    }

    public double getBrokerage() {
        return brokerage;
    }

    public void setBrokerage(double brokerage) {
        this.brokerage = brokerage;
    }

    public double getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(double netAmount) {
        this.netAmount = netAmount;
    }

    public void setTransactionCharges(double transactionCharges) {
        this.transactionCharges = transactionCharges;
    }

    public double getTransactionCharges(){
        return transactionCharges;
    }
}
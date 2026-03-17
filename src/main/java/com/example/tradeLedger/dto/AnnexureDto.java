package com.example.tradeLedger.dto;

import lombok.Data;

@Data
public class AnnexureDto {

    private String contract;
    private String buySell;
    private int quantity;
    private double wap;
    private double netTotal;

    public AnnexureDto() {
    }

    public AnnexureDto(String contract, String buySell, int quantity, double wap, double netTotal) {
        this.contract = contract;
        this.buySell = buySell;
        this.quantity = quantity;
        this.wap = wap;
        this.netTotal = netTotal;
    }

    public String getContract() {
        return contract;
    }

    public void setContract(String contract) {
        this.contract = contract;
    }

    public String getBuySell() {
        return buySell;
    }

    public void setBuySell(String buySell) {
        this.buySell = buySell;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getWap() {
        return wap;
    }

    public void setWap(double wap) {
        this.wap = wap;
    }

    public double getNetTotal() {
        return netTotal;
    }

    public void setNetTotal(double netTotal) {
        this.netTotal = netTotal;
    }
}
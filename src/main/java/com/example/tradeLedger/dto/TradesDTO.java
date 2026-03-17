package com.example.tradeLedger.dto;

import lombok.Data;

@Data
public class TradesDTO {

    private Long id;
    private Integer year;
    private Integer month;
    private Integer day;
    private Double pnl;
    private Integer noOfTrades;

    public TradesDTO() {
    }

    public TradesDTO(Long id, Integer year, Integer month, Integer day, Double pnl, Integer noOfTrades) {
        this.id = id;
        this.year = year;
        this.month = month;
        this.day = day;
        this.pnl = pnl;
        this.noOfTrades = noOfTrades;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public Integer getDay() {
        return day;
    }

    public void setDay(Integer day) {
        this.day = day;
    }

    public Double getPnl() {
        return pnl;
    }

    public void setPnl(Double pnl) {
        this.pnl = pnl;
    }

    public Integer getNoOfTrades() {
        return noOfTrades;
    }

    public void setNoOfTrades(Integer noOfTrades) {
        this.noOfTrades = noOfTrades;
    }
}
package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "trades")
@Data
public class Trades {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "year")
    private Integer year;

    @Column(name = "month")
    private Integer month;

    @Column(name = "day")
    private Integer day;

    @Column(name = "pnl")
    private Double pnl;

    @Column(name = "no_of_trades")
    private Integer noOfTrades;

    @Column(name = "email")
    private String email;

    public Trades() {
    }

    public Trades(Long id, Integer year, Integer month, Integer day, Double pnl, Integer noOfTrades) {
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "daily_target")
@Data

public class DailyTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "year")
    private int year;

    @Column(name = "month")
    private int month;

    @Column(name = "day")
    private int day;

    @Column(name = "daily_target")
    private double dailyTarget;

    public DailyTarget() {
    }

    public DailyTarget(Long id, int year, int month, int day, double dailyTarget) {
        this.id = id;
        this.year = year;
        this.month = month;
        this.day = day;
        this.dailyTarget = dailyTarget;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public double getDailyTarget() {
        return dailyTarget;
    }

    public void setDailyTarget(double dailyTarget) {
        this.dailyTarget = dailyTarget;
    }
}
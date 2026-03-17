package com.example.tradeLedger.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "monthly_target")
@Data

public class MonthlyTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "year")
    private int year;

    @Column(name = "month")
    private int month;

    @Column(name = "target")
    private double target;

    @Column(name = "achieved")
    private double achieved;

    public MonthlyTarget() {
    }

    public MonthlyTarget(Long id, int year, int month, double target, double achieved) {
        this.id = id;
        this.year = year;
        this.month = month;
        this.target = target;
        this.achieved = achieved;
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

    public double getTarget() {
        return target;
    }

    public void setTarget(double target) {
        this.target = target;
    }

    public double getAchieved() {
        return achieved;
    }

    public void setAchieved(double achieved) {
        this.achieved = achieved;
    }
}
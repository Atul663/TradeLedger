package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.Trades;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TradesRepository extends JpaRepository<Trades, Long> {

    @Query(value = "Select * from trades where day = :day and month = :month and year = :year", nativeQuery = true)
    Trades getTodayTrade(int day, int month, int year);

    @Query(value = "Select * from trades where month = :month and year = :year", nativeQuery = true)
    List<Trades> getMonthTrade(Integer month, Integer year);

    @Query(value = "Select * from trades where year = :year", nativeQuery = true)
    List<Trades> getYearTrade(Integer year);
}
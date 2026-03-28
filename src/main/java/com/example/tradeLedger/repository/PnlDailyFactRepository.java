package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.PnlDailyFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface PnlDailyFactRepository extends JpaRepository<PnlDailyFact, Long> {

    Optional<PnlDailyFact> findByTradingDay_Id(Long tradingDayId);

    @Query("""
            select coalesce(sum(df.netPnl), 0)
            from PnlDailyFact df
            join df.tradingDay td
            join td.planMonth pm
            where pm.plan.id = :planId
              and td.tradeDate < :beforeDate
            """)
    BigDecimal sumNetPnlByPlanIdBeforeDate(@Param("planId") Long planId, @Param("beforeDate") LocalDate beforeDate);

    @Query("""
            select coalesce(sum(df.netPnl), 0)
            from PnlDailyFact df
            join df.tradingDay td
            join td.planMonth pm
            where pm.plan.id = :planId
            """)
    BigDecimal sumNetPnlByPlanId(@Param("planId") Long planId);

    @Query("""
            select pm.plan.id, coalesce(sum(df.netPnl), 0)
            from PnlDailyFact df
            join df.tradingDay td
            join td.planMonth pm
            where pm.plan.user.id = :userId
            group by pm.plan.id
            """)
    java.util.List<Object[]> sumNetPnlByPlanForUser(@Param("userId") Long userId);
}

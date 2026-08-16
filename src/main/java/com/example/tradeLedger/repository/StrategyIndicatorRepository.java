package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.StrategyIndicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * The derived strategy-to-indicator index. Rebuilt from each strategy's rule
 * tree on save; see {@link com.example.tradeLedger.entity.StrategyIndicator}.
 */
public interface StrategyIndicatorRepository extends JpaRepository<StrategyIndicator, UUID> {

    List<StrategyIndicator> findByStrategy_IdOrderByIndicator_NameAsc(UUID strategyId);

    /** The reverse lookup the indicator catalog needs, without scanning rule trees. */
    List<StrategyIndicator> findByIndicator_IdOrderByStrategy_NameAsc(UUID indicatorId);

    boolean existsByIndicator_Id(UUID indicatorId);

    void deleteByStrategy_Id(UUID strategyId);
}

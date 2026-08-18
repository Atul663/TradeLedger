package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.StrategyIndicatorLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * The derived strategy-to-indicator index. Rebuilt from each strategy's rule
 * tree on save; see {@link com.example.tradeLedger.entity.StrategyIndicatorLink}.
 */
public interface StrategyIndicatorLinkRepository extends JpaRepository<StrategyIndicatorLink, UUID> {

    List<StrategyIndicatorLink> findByStrategy_IdOrderByIndicator_NameAsc(UUID strategyId);

    /** The reverse lookup the indicator catalog needs, without scanning rule trees. */
    List<StrategyIndicatorLink> findByIndicator_IdOrderByStrategy_NameAsc(UUID indicatorId);

    boolean existsByIndicator_Id(UUID indicatorId);

    /** Guards a user strategy from attaching an indicator its template does not declare. */
    boolean existsByStrategy_IdAndIndicator_Id(UUID strategyId, UUID indicatorId);

    void deleteByStrategy_Id(UUID strategyId);
}

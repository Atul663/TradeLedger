package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.SharedStrategyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharedStrategyConfigRepository extends JpaRepository<SharedStrategyConfig, UUID> {

    /**
     * The dedup lookup, mirroring
     * UNIQUE (strategy_id, symbol_id, timeframe, config_hash):
     * same math always lands on the same row, whoever asks for it.
     */
    Optional<SharedStrategyConfig> findByStrategy_IdAndSymbol_IdAndTimeframeAndConfigHash(
            UUID strategyId, UUID symbolId, String timeframe, String configHash);

    List<SharedStrategyConfig> findByStatusOrderByCreatedAtDesc(String status);

    List<SharedStrategyConfig> findByStrategy_IdOrderByCreatedAtDesc(UUID strategyId);

    long countByStrategy_Id(UUID strategyId);

    /**
     * The same count for MANY templates at once - one query per grouped response
     * rather than one per group.
     *
     * {@code [strategyId, count]} pairs; a template with no shared computations is
     * absent rather than zero, so callers default a miss to 0.
     */
    @Query("""
            select c.strategy.id, count(c)
              from SharedStrategyConfig c
             where c.strategy.id in :strategyIds
             group by c.strategy.id""")
    List<Object[]> countByStrategyIds(@Param("strategyIds") Collection<UUID> strategyIds);

    long countByStrategy_IdAndStatus(UUID strategyId, String status);
}

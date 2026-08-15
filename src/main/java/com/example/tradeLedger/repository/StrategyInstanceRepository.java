package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.StrategyInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StrategyInstanceRepository extends JpaRepository<StrategyInstance, UUID> {

    /**
     * The dedup lookup, mirroring
     * UNIQUE (strategy_id, symbol_id, timeframe, config_hash):
     * same math always lands on the same row, whoever asks for it.
     */
    Optional<StrategyInstance> findByStrategy_IdAndSymbol_IdAndTimeframeAndConfigHash(
            UUID strategyId, UUID symbolId, String timeframe, String configHash);

    List<StrategyInstance> findByStatusOrderByCreatedAtDesc(String status);

    List<StrategyInstance> findByStrategy_IdOrderByCreatedAtDesc(UUID strategyId);

    long countByStrategy_Id(UUID strategyId);

    long countByStrategy_IdAndStatus(UUID strategyId, String status);
}

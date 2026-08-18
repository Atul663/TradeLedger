package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.SharedStrategyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

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

    long countByStrategy_IdAndStatus(UUID strategyId, String status);
}

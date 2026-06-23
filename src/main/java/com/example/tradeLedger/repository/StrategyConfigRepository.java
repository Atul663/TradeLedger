package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.StrategyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StrategyConfigRepository extends JpaRepository<StrategyConfig, Long> {
    Optional<StrategyConfig> findByStrategyName(String strategyName);
}

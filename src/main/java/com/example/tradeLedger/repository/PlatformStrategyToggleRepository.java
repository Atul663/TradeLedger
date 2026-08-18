package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.PlatformStrategyToggle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformStrategyToggleRepository extends JpaRepository<PlatformStrategyToggle, Long> {
    Optional<PlatformStrategyToggle> findByStrategyName(String strategyName);
}

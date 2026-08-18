package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.StrategyTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StrategyTemplateRepository extends JpaRepository<StrategyTemplate, UUID> {

    /** strategies.name is UNIQUE - it is the strategy's business key. */
    Optional<StrategyTemplate> findByName(String name);

    boolean existsByName(String name);

    List<StrategyTemplate> findAllByOrderByNameAsc();

    List<StrategyTemplate> findByActiveOrderByNameAsc(boolean active);

    List<StrategyTemplate> findByNameContainingIgnoreCaseOrderByNameAsc(String fragment);

    List<StrategyTemplate> findByActiveAndNameContainingIgnoreCaseOrderByNameAsc(boolean active, String fragment);
}

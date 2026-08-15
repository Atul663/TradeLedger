package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StrategyRepository extends JpaRepository<Strategy, UUID> {

    /** strategies.name is UNIQUE - it is the strategy's business key. */
    Optional<Strategy> findByName(String name);

    boolean existsByName(String name);

    List<Strategy> findAllByOrderByNameAsc();

    List<Strategy> findByActiveOrderByNameAsc(boolean active);

    List<Strategy> findByNameContainingIgnoreCaseOrderByNameAsc(String fragment);

    List<Strategy> findByActiveAndNameContainingIgnoreCaseOrderByNameAsc(boolean active, String fragment);
}

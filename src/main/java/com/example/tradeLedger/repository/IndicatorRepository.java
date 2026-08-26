package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.Indicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IndicatorRepository extends JpaRepository<Indicator, UUID> {

    /** indicators.name is UNIQUE and is what a rule tree's "ind" value resolves to. */
    Optional<Indicator> findByName(String name);

    /**
     * Names are stored in their display casing ('EMA Averaging'), so every lookup that
     * starts from user input matches without regard to case - otherwise the casing a
     * caller happens to type decides whether a catalogued indicator exists.
     */
    Optional<Indicator> findByNameIgnoreCase(String name);

    boolean existsByName(String name);

    /** Guards the UNIQUE name against a duplicate that differs only by case. */
    boolean existsByNameIgnoreCase(String name);

    List<Indicator> findAllByOrderByNameAsc();

    List<Indicator> findByActiveOrderByNameAsc(boolean active);
}

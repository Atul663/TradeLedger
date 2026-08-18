package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.Indicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IndicatorRepository extends JpaRepository<Indicator, UUID> {

    /** indicators.name is UNIQUE and is what a rule tree's "ind" value resolves to. */
    Optional<Indicator> findByName(String name);

    boolean existsByName(String name);

    List<Indicator> findAllByOrderByNameAsc();

    List<Indicator> findByActiveOrderByNameAsc(boolean active);
}

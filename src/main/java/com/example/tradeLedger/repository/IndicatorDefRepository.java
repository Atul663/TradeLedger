package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.IndicatorDef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IndicatorDefRepository extends JpaRepository<IndicatorDef, UUID> {

    /** indicator_defs.name is UNIQUE and is what a rule tree's "ind" value resolves to. */
    Optional<IndicatorDef> findByName(String name);

    boolean existsByName(String name);

    List<IndicatorDef> findAllByOrderByNameAsc();

    List<IndicatorDef> findByActiveOrderByNameAsc(boolean active);
}

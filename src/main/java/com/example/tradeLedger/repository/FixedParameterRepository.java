package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.FixedParameter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The fixed-knob descriptor catalog.
 *
 * Two reads, both ordered the way a form lays the fields out - by group, then
 * position within it, then name so the ordering is total. The group and scope
 * filters are applied by the service over the returned list rather than as more
 * finder methods: this is a catalog of a few dozen descriptor rows, and the
 * combinations would otherwise multiply into a method per pair.
 */
public interface FixedParameterRepository extends JpaRepository<FixedParameter, UUID> {

    /** The business key. Matched case-insensitively; 'slPct' and 'slpct' are one knob. */
    Optional<FixedParameter> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<FixedParameter> findAllByOrderByParamGroupAscDisplayOrderAscNameAsc();

    List<FixedParameter> findByActiveOrderByParamGroupAscDisplayOrderAscNameAsc(boolean active);
}

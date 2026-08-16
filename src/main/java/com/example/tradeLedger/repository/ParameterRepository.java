package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.Parameter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** The parameter catalog. {@code code} is the business key. */
public interface ParameterRepository extends JpaRepository<Parameter, Long> {

    Optional<Parameter> findByCode(String code);

    boolean existsByCode(String code);

    List<Parameter> findAllByOrderByDisplayOrderAscCodeAsc();

    List<Parameter> findByScopeOrderByDisplayOrderAscCodeAsc(String scope);

    /** Attached to every strategy automatically - see Parameter.universal. */
    List<Parameter> findByUniversalTrueOrderByDisplayOrderAscCodeAsc();
}

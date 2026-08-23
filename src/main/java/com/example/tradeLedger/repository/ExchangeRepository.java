package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRepository extends JpaRepository<Exchange, UUID> {

    Optional<Exchange> findByCode(String code);

    List<Exchange> findByStatusOrderByNameAsc(String status);

    List<Exchange> findAllByOrderByNameAsc();

    boolean existsByCode(String code);

    boolean existsByName(String name);
}

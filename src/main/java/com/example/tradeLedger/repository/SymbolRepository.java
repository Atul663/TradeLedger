package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.Symbol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SymbolRepository extends JpaRepository<Symbol, UUID> {

    /** UNIQUE (exchange_id, symbol). */
    Optional<Symbol> findByExchange_IdAndSymbol(UUID exchangeId, String symbol);

    List<Symbol> findByActiveTrueOrderBySymbolAsc();

    List<Symbol> findByExchange_IdOrderBySymbolAsc(UUID exchangeId);

    List<Symbol> findByExchange_IdAndActiveTrueOrderBySymbolAsc(UUID exchangeId);

    /** Guards exchange deletion: a venue with instruments on it is still in use. */
    long countByExchange_Id(UUID exchangeId);
}

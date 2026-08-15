package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.TradingAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradingAccountRepository extends JpaRepository<TradingAccount, UUID> {

    List<TradingAccount> findByUser_IdOrderByAccountNameAsc(UUID userId);

    /** Ownership-scoped read: an account is never reachable across users. */
    Optional<TradingAccount> findByIdAndUser_Id(UUID id, UUID userId);

    boolean existsByUser_IdAndExchange_IdAndAccountName(UUID userId, UUID exchangeId, String accountName);
}

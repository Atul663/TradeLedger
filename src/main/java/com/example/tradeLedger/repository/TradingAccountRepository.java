package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.TradingAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradingAccountRepository extends JpaRepository<TradingAccount, UUID> {

    List<TradingAccount> findByUser_IdOrderByAccountNameAsc(UUID userId);

    List<TradingAccount> findByUserBroker_IdOrderByAccountNameAsc(UUID userBrokerId);

    /** Ownership-scoped read: an account is never reachable across users. */
    Optional<TradingAccount> findByIdAndUser_Id(UUID id, UUID userId);

    /**
     * Duplicate-name check, scoped to one setup.
     *
     * Names only have to be unique within the setup they belong to, so a Delta
     * "main" and a Dhan "main" coexist without either being a conflict.
     */
    boolean existsByUserBroker_IdAndAccountName(UUID userBrokerId, String accountName);

    long countByUserBroker_Id(UUID userBrokerId);
}

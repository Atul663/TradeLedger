package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.AccountCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountCredentialRepository extends JpaRepository<AccountCredential, UUID> {

    /** 1:1 with trading_accounts. */
    Optional<AccountCredential> findByTradingAccount_Id(UUID tradingAccountId);

    void deleteByTradingAccount_Id(UUID tradingAccountId);
}

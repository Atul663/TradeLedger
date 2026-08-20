package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.BrokerCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrokerCredentialRepository extends JpaRepository<BrokerCredential, UUID> {

    /** The setup's own credentials - the row every account under it inherits. */
    Optional<BrokerCredential> findByUserBroker_IdAndTradingAccountIsNull(UUID userBrokerId);

    /** One account's override, when it has one. */
    Optional<BrokerCredential> findByTradingAccount_Id(UUID tradingAccountId);

    List<BrokerCredential> findByUserBroker_Id(UUID userBrokerId);

    long countByUserBroker_IdAndTradingAccountIsNotNull(UUID userBrokerId);

    void deleteByTradingAccount_Id(UUID tradingAccountId);
}

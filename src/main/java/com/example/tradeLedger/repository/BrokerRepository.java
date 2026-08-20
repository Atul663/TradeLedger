package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.Broker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrokerRepository extends JpaRepository<Broker, UUID> {

    Optional<Broker> findByCodeIgnoreCase(String code);

    List<Broker> findAllByOrderByNameAsc();

    List<Broker> findByActiveTrueOrderByNameAsc();
}

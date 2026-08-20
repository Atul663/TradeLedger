package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.UserBroker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBrokerRepository extends JpaRepository<UserBroker, UUID> {

    List<UserBroker> findByUser_IdOrderByLabelAsc(UUID userId);

    List<UserBroker> findByUser_IdAndBroker_IdOrderByLabelAsc(UUID userId, UUID brokerId);

    /** Ownership-scoped read: another user's setup is never reachable. */
    Optional<UserBroker> findByIdAndUser_Id(UUID id, UUID userId);

    boolean existsByUser_IdAndLabelIgnoreCase(UUID userId, String label);

    long countByBroker_Id(UUID brokerId);
}

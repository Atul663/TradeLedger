package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.UserStrategy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserStrategyRepository extends JpaRepository<UserStrategy, UUID> {

    List<UserStrategy> findByUser_IdOrderByCreatedAtAsc(UUID userId);

    List<UserStrategy> findByUser_IdAndActiveOrderByCreatedAtAsc(UUID userId, boolean active);

    List<UserStrategy> findByUser_IdAndStrategy_IdOrderByCreatedAtAsc(UUID userId, UUID strategyId);

    /** Every read is filtered by owner: someone else's row is a 404, never a 403. */
    Optional<UserStrategy> findByIdAndUser_Id(UUID id, UUID userId);

    /** UNIQUE (user_id, name) - two users may both name one "My EMA". */
    Optional<UserStrategy> findByUser_IdAndNameIgnoreCase(UUID userId, String name);

    /** Guards template deletion: a template customized by users is still in use. */
    long countByStrategy_Id(UUID strategyId);

    /** Guards symbol deletion: an underlying somebody's strategy watches is still in use. */
    long countBySymbol_Id(UUID symbolId);
}

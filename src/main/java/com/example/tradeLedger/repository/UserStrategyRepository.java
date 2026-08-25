package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.UserStrategy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserStrategyRepository extends JpaRepository<UserStrategy, UUID> {

    /**
     * The four associations every read of a strategy dereferences.
     *
     * All four are {@code LAZY} on the entity, which is right for a write path
     * that only touches columns - but a response names the template, the ticker,
     * the ticker's venue and the shared config on EVERY row, so leaving them lazy
     * costs four extra round trips per strategy. Naming them here folds those into
     * the query that was being issued anyway.
     *
     * They are all {@code @ManyToOne}, so this is a join with no row multiplication
     * and no pagination hazard - unlike fetching a collection, which would need a
     * distinct pass. The paths are repeated on each finder because an annotation
     * argument has to be a constant expression.
     */
    @EntityGraph(attributePaths = {"strategy", "symbol", "symbol.exchange", "sharedConfig"})
    List<UserStrategy> findByUser_IdOrderByCreatedAtAsc(UUID userId);

    @EntityGraph(attributePaths = {"strategy", "symbol", "symbol.exchange", "sharedConfig"})
    List<UserStrategy> findByUser_IdAndActiveOrderByCreatedAtAsc(UUID userId, boolean active);

    @EntityGraph(attributePaths = {"strategy", "symbol", "symbol.exchange", "sharedConfig"})
    List<UserStrategy> findByUser_IdAndStrategy_IdOrderByCreatedAtAsc(UUID userId, UUID strategyId);

    /** Every read is filtered by owner: someone else's row is a 404, never a 403. */
    @EntityGraph(attributePaths = {"strategy", "symbol", "symbol.exchange", "sharedConfig"})
    Optional<UserStrategy> findByIdAndUser_Id(UUID id, UUID userId);

    /** UNIQUE (user_id, name) - two users may both name one "My EMA". */
    Optional<UserStrategy> findByUser_IdAndNameIgnoreCase(UUID userId, String name);

    /** Guards template deletion: a template customized by users is still in use. */
    long countByStrategy_Id(UUID strategyId);

    /** Guards symbol deletion: an underlying somebody's strategy watches is still in use. */
    long countBySymbol_Id(UUID symbolId);
}

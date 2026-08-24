package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.StrategyDeployRequest;
import com.example.tradeLedger.dto.StrategyDeploymentResponse;
import com.example.tradeLedger.dto.UserStrategyGroupResponse;
import com.example.tradeLedger.dto.UserStrategyRequest;
import com.example.tradeLedger.dto.UserStrategyResponse;
import com.example.tradeLedger.dto.UserStrategyRuntimeResponse;

import java.util.List;
import java.util.UUID;

/**
 * A user's own strategies: the complete, runnable configurations they build from
 * the platform templates.
 *
 * A strategy holds its whole configuration in typed columns - the market, the
 * instrument, the strikes, the averaging ladder, the exits - plus one row per
 * indicator carrying that indicator values as jsonb. The template supplies the
 * logic and is never written from here.
 *
 * Two read shapes over the same rows, because the two consumers want different
 * things:
 * <ul>
 *   <li>{@link #get} - the editor shape: every column as it is stored, plus each
 *       indicator schema so the form can render itself</li>
 *   <li>{@link #runtime} - the bot shape: legs resolved, values coerced, and the
 *       signal params exactly as they were hashed</li>
 * </ul>
 *
 * Every method takes the authenticated caller email and scopes its work to that
 * user rows. Someone else strategy is not "forbidden", it is not found.
 */
public interface UserStrategyService {

    /**
     * @param active     true/false to filter by the archive flag, null for all
     * @param strategyId only strategies built from this template, or null for all
     */
    List<UserStrategyResponse> list(String email, Boolean active, UUID strategyId);

    /**
     * The same rows {@link #list} returns, arranged one group per template.
     *
     * A user builds several customizations of the same template - one per market,
     * one per tuning - so a list screen wants a heading per template rather than a
     * flat run of rows. The grouping key is {@code user_strategies.strategy_id} and
     * the group carries the template name as its tag.
     *
     * Groups come back ordered by that name, rows inside a group oldest first, and
     * a template the caller has built nothing from produces no group at all.
     *
     * @param active     true/false to filter by the archive flag, null for all
     * @param strategyId only the group for this template, or null for every group
     */
    List<UserStrategyGroupResponse> listGrouped(String email, Boolean active, UUID strategyId);

    UserStrategyResponse get(String email, UUID id);

    /**
     * Build a strategy from a template. Absent fields take their column defaults
     * and absent indicator values take their schema defaults, so a body naming
     * only the template saves a runnable strategy on platform defaults.
     */
    UserStrategyResponse create(String email, UserStrategyRequest request);

    /**
     * Partial update. A present field is applied, an absent one is left alone.
     * Changing anything that feeds the config hash repoints the strategy at a new
     * shared computation and retires the old one once nobody is left on it.
     */
    UserStrategyResponse update(String email, UUID id, UserStrategyRequest request);

    /** Refused while the strategy is still deployed on any broker. */
    void delete(String email, UUID id);

    /** The bot read: everything resolved, nothing left to look up. */
    UserStrategyRuntimeResponse runtime(String email, UUID id);

    /**
     * Deploy one strategy onto many brokers in one call.
     *
     * Each account is subscribed in its own transaction and reports its own
     * outcome, so one account that already runs this strategy does not stop the
     * others from starting.
     */
    StrategyDeploymentResponse deploy(String email, UUID id, StrategyDeployRequest request);
}

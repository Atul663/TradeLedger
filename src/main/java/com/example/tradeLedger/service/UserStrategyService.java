package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.StrategySubscriptionResponse;
import com.example.tradeLedger.dto.UserStrategyRequest;
import com.example.tradeLedger.dto.UserStrategyResponse;
import com.example.tradeLedger.dto.UserStrategyRuntimeResponse;
import com.example.tradeLedger.dto.UserStrategySubscribeRequest;
import com.example.tradeLedger.dto.UserStrategyUpdateRequest;

import java.util.List;
import java.util.UUID;

/**
 * A user's own customizations of the global strategy templates.
 *
 * The global template, its indicators and the parameter catalog are shared by
 * every user and are never written from here. What a user owns is a
 * {@code user_strategies} row, the indicator usages under it, and one row per
 * knob they actually changed - so an admin retuning a global default moves every
 * user who left that knob alone, and moves nobody who overrode it.
 *
 * Two read shapes over the same tables, because the two consumers want different
 * things:
 * <ul>
 *   <li>{@link #get} - the UI's shape: template, indicators, and every knob with
 *       its global default and the user's value side by side</li>
 *   <li>{@link #runtime} - the bot's shape: the same knobs collapsed to the
 *       values in force, already split into signal and execution scope</li>
 * </ul>
 *
 * Every method takes the authenticated caller's email and scopes its work to that
 * user's rows. Someone else's user strategy is not "forbidden", it is not found.
 */
public interface UserStrategyService {

    /**
     * @param active     true/false to filter by the archive flag, null for all
     * @param strategyId only customizations of this template, or null for all
     */
    List<UserStrategyResponse> list(String email, Boolean active, UUID strategyId);

    UserStrategyResponse get(String email, UUID id);

    /**
     * Customize a template. The indicator rows are created from the template's own
     * indicators, and only the knobs named in {@code overrides} get a row - a
     * request with none saves a faithful copy sitting on global defaults.
     */
    UserStrategyResponse create(String email, UserStrategyRequest request);

    /**
     * Partial update. Overrides are applied entry by entry; an entry with a null
     * value clears that override and returns the knob to the global default.
     */
    UserStrategyResponse update(String email, UUID id, UserStrategyUpdateRequest request);

    void delete(String email, UUID id);

    /**
     * The bot's read: indicators and their effective values, resolved
     * custom &rarr; indicator default &rarr; catalog default, with the signal /
     * execution split already applied.
     */
    UserStrategyRuntimeResponse runtime(String email, UUID id);

    /**
     * Put a user strategy to work on a trading account. Its effective values are
     * projected into the flat map the execution path already consumes, so dedup
     * and the config hash keep working untouched.
     */
    StrategySubscriptionResponse subscribe(String email, UUID id, UserStrategySubscribeRequest request);
}

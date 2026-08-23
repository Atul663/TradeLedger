package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.ExchangeRequest;
import com.example.tradeLedger.dto.ExchangeResponse;
import com.example.tradeLedger.dto.RiskProfileRequest;
import com.example.tradeLedger.dto.RiskProfileResponse;
import com.example.tradeLedger.dto.SymbolRequest;
import com.example.tradeLedger.dto.SymbolResponse;
import com.example.tradeLedger.dto.UserRiskLimitRequest;
import com.example.tradeLedger.dto.UserRiskLimitResponse;

import java.util.List;
import java.util.UUID;

/**
 * The platform reference data a strategy configuration has to point at -
 * {@code exchanges}, {@code symbols}, {@code risk_profiles} - plus the caller's
 * own {@code user_risk_limits}.
 *
 * <b>The first three are SHARED master data with no owner column.</b> Reads are
 * therefore unfiltered and writes are not scoped to the caller: an exchange
 * created here is visible to every user, and deleting a symbol would take it away
 * from every user watching it. This API layer has no role model to gate that
 * with, so the protection is structural instead - the same stance
 * {@code /api/v1/brokers} already takes:
 *
 * <ul>
 *   <li>a delete is <b>refused</b> while anything references the row (409), so no
 *       write here can break a configuration somebody already saved</li>
 *   <li>deactivating is always available and is the intended alternative</li>
 *   <li>identity columns ({@code exchanges.code}, a symbol's exchange) are
 *       immutable once other rows point at them</li>
 * </ul>
 *
 * That makes the worst case additive clutter rather than data loss. It is not a
 * substitute for authorization - see the known gaps in the architecture doc.
 *
 * {@code user_risk_limits} is the exception: one row per user, keyed by the
 * caller, and scoped like the rest of the module.
 */
public interface ReferenceDataService {

    // ------------------------------------------------------------ exchanges

    /** @param status 'active' / 'disabled', or null for all */
    List<ExchangeResponse> listExchanges(String status);

    ExchangeResponse getExchange(UUID id);

    ExchangeResponse createExchange(ExchangeRequest request);

    /** Partial. {@code code} cannot change while any symbol hangs off the venue. */
    ExchangeResponse updateExchange(UUID id, ExchangeRequest request);

    /** Refused while any symbol belongs to it - disable it instead. */
    void deleteExchange(UUID id);

    // -------------------------------------------------------------- symbols

    /**
     * @param exchangeId optional filter
     * @param activeOnly when true, hides symbols the expiry sweeper has retired
     */
    List<SymbolResponse> listSymbols(UUID exchangeId, boolean activeOnly);

    SymbolResponse getSymbol(UUID id);

    SymbolResponse createSymbol(SymbolRequest request);

    /** Partial. The exchange is fixed once set - a symbol cannot change venue. */
    SymbolResponse updateSymbol(UUID id, SymbolRequest request);

    /** Refused while any strategy watches it - deactivate it instead. */
    void deleteSymbol(UUID id);

    // -------------------------------------------------------- risk profiles

    List<RiskProfileResponse> listRiskProfiles();

    RiskProfileResponse getRiskProfile(UUID id);

    RiskProfileResponse createRiskProfile(RiskProfileRequest request);

    RiskProfileResponse updateRiskProfile(UUID id, RiskProfileRequest request);

    /** Refused while any deployment runs under it. */
    void deleteRiskProfile(UUID id);

    // ---------------------------------------------------------- user limits

    /** The caller's aggregate caps; all-null when none have been set. */
    UserRiskLimitResponse getRiskLimits(String email);

    /** Upsert of the caller's aggregate caps. */
    UserRiskLimitResponse setRiskLimits(String email, UserRiskLimitRequest request);
}

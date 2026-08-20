package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.BrokerResponse;
import com.example.tradeLedger.dto.ExchangeResponse;
import com.example.tradeLedger.dto.RiskProfileResponse;
import com.example.tradeLedger.dto.SymbolResponse;
import com.example.tradeLedger.dto.UserRiskLimitRequest;
import com.example.tradeLedger.dto.UserRiskLimitResponse;

import java.util.List;
import java.util.UUID;

/**
 * Read access to the platform reference data a strategy configuration has to
 * point at - {@code exchanges}, {@code symbols}, {@code risk_profiles} - plus
 * the caller's own {@code user_risk_limits}.
 *
 * The first three are deliberately read-only here. They are operational master
 * data: symbols come from the exchange's instrument feed and the expiry sweeper,
 * risk profiles are set by whoever governs the platform. The schema gives them
 * no owner column and this API layer has no role model to gate writes with, so
 * exposing writes would let any authenticated user reshape data every other user
 * depends on. Seed them through {@code db/control-plane-schema.sql}.
 */
public interface ReferenceDataService {

    /** @param status 'active' / 'disabled', or null for all */
    List<ExchangeResponse> listExchanges(String status);

    ExchangeResponse getExchange(UUID id);

    /**
     * The brokers an account can be opened through.
     *
     * Separate from {@link #listExchanges} because the two answer different
     * questions: an exchange is where an instrument trades, a broker is who the
     * order is routed through. A credential form reads {@code authType} from
     * here to know which fields that particular broker needs.
     *
     * @param activeOnly hides brokers the platform has retired
     */
    List<BrokerResponse> listBrokers(boolean activeOnly);

    BrokerResponse getBroker(UUID id);

    /**
     * @param exchangeId optional filter
     * @param activeOnly when true, hides symbols the expiry sweeper has retired
     */
    List<SymbolResponse> listSymbols(UUID exchangeId, boolean activeOnly);

    SymbolResponse getSymbol(UUID id);

    List<RiskProfileResponse> listRiskProfiles();

    RiskProfileResponse getRiskProfile(UUID id);

    /** The caller's aggregate caps; all-null when none have been set. */
    UserRiskLimitResponse getRiskLimits(String email);

    /** Upsert of the caller's aggregate caps. */
    UserRiskLimitResponse setRiskLimits(String email, UserRiskLimitRequest request);
}

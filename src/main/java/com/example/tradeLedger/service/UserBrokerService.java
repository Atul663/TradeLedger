package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.UserBrokerRequest;
import com.example.tradeLedger.dto.UserBrokerResponse;

import java.util.List;
import java.util.UUID;

/**
 * A user's broker setups ({@code user_brokers}) - step one of getting an account
 * ready to trade.
 *
 * The flow the API expects:
 * <ol>
 *   <li>{@code POST /api/v1/my-brokers} - set the broker up</li>
 *   <li>{@code PUT /api/v1/my-brokers/{id}/credentials} - give it an API key</li>
 *   <li>{@code POST /api/v1/trading-accounts} - create the accounts under it</li>
 * </ol>
 *
 * Everything is scoped to the caller; another user's setup reports 404, not 403.
 */
public interface UserBrokerService {

    /** @param brokerId optional filter to one catalog broker */
    List<UserBrokerResponse> list(String email, UUID brokerId, Boolean active);

    UserBrokerResponse get(String email, UUID id);

    UserBrokerResponse create(String email, UserBrokerRequest request);

    /** Partial. The catalog broker cannot be changed - that is a different setup. */
    UserBrokerResponse update(String email, UUID id, UserBrokerRequest request);

    /** Refused while trading accounts still hang off it. */
    void delete(String email, UUID id);
}

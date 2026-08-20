package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.TradingAccountRequest;
import com.example.tradeLedger.dto.TradingAccountResponse;

import java.util.List;
import java.util.UUID;

/**
 * The accounts under a user's broker setups ({@code trading_accounts}).
 *
 * Step three of the flow: {@link UserBrokerService} sets the broker up and
 * {@link BrokerCredentialService} gives it a key, then accounts are created here.
 * An account inherits its setup's credentials unless it overrides them.
 *
 * Part of this module because {@code subscriptions.trading_account_id} is NOT
 * NULL: a strategy cannot be subscribed to without one, so the strategy API is
 * unusable without this.
 */
public interface TradingAccountService {

    /** @param userBrokerId optional filter to the accounts under one setup */
    List<TradingAccountResponse> list(String email, UUID userBrokerId);

    TradingAccountResponse get(String email, UUID id);

    TradingAccountResponse create(String email, TradingAccountRequest request);

    /** Partial. An account cannot move between setups - that is a new account. */
    TradingAccountResponse update(String email, UUID id, TradingAccountRequest request);

    /** Refused while active subscriptions still execute on the account. */
    void delete(String email, UUID id);
}

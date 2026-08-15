package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.TradingAccountRequest;
import com.example.tradeLedger.dto.TradingAccountResponse;

import java.util.List;
import java.util.UUID;

/**
 * The caller's broker accounts ({@code trading_accounts} + their 1:1
 * {@code account_credentials} vault reference).
 *
 * Part of this module because {@code subscriptions.trading_account_id} is NOT
 * NULL: a strategy cannot be subscribed to without one, so the strategy API is
 * unusable without this.
 */
public interface TradingAccountService {

    List<TradingAccountResponse> list(String email);

    TradingAccountResponse get(String email, UUID id);

    TradingAccountResponse create(String email, TradingAccountRequest request);

    TradingAccountResponse update(String email, UUID id, TradingAccountRequest request);

    /** Refused while active subscriptions still execute on the account. */
    void delete(String email, UUID id);
}

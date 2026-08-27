package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.BrokerCredentialFieldRequest;
import com.example.tradeLedger.dto.BrokerCredentialFieldResponse;

import java.util.List;
import java.util.UUID;

/**
 * CRUD over {@code broker_credential_fields} - the catalog describing what a
 * broker's credential form renders.
 *
 * <b>Descriptors, not credentials.</b> The value of every field described here is
 * a column on {@code broker_credentials}, encrypted by {@code SecretCipher} and
 * written through {@link UserBrokerService} and {@link TradingAccountService}.
 * Nothing in this module reads, writes or reveals a secret; it changes what a
 * form shows. That boundary is the same one {@link FixedParameterService}
 * documents, and it is stricter here because the thing being described IS a
 * secret - a {@code secret} field is refused a default value for exactly that
 * reason.
 *
 * The rows are SHARED platform master data with no owner column, so reads are
 * unfiltered and writes are not scoped to the caller - the stance
 * {@link ReferenceDataService} documents, and not yet gated by role. The blast
 * radius is a mislabelled input, not a leaked key.
 *
 * The catalog is seeded from {@code control-plane-schema.sql} with a descriptor
 * per credential column per broker, and this API is how those are corrected and
 * how a new broker gets a form without a UI release.
 */
public interface BrokerCredentialFieldService {

    /**
     * Ordered by group, then position, then field key - the way a form lays the
     * inputs out.
     *
     * @param brokerId   optional broker filter; takes precedence over brokerCode
     * @param brokerCode optional broker filter by catalog code, case-insensitive
     * @param group      optional 'credentials' / 'session' filter
     * @param active     optional active-flag filter; null returns both
     */
    List<BrokerCredentialFieldResponse> list(UUID brokerId, String brokerCode, String group,
                                             Boolean active);

    BrokerCredentialFieldResponse get(UUID id);

    /**
     * By the business key - the lookup a form uses when it holds a broker and the
     * credential column it is rendering.
     */
    BrokerCredentialFieldResponse getByBrokerAndKey(UUID brokerId, String fieldKey);

    BrokerCredentialFieldResponse create(BrokerCredentialFieldRequest request);

    /** Partial. An absent field keeps its stored value. */
    BrokerCredentialFieldResponse update(UUID id, BrokerCredentialFieldRequest request);

    /**
     * Hard delete. Nothing points at a descriptor and the column it describes is
     * unaffected - but deactivating is the reversible path, and the one that
     * leaves the stored credential explained rather than orphaned.
     */
    void delete(UUID id);
}

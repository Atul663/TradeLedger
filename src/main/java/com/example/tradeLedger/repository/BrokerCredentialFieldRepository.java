package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.BrokerCredentialField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The credential-form descriptor catalog.
 *
 * Every read is ordered the way a form lays the fields out - by group, then
 * position within it, then field key so the ordering is total. Shared platform
 * data, so unlike the credential repositories nothing here is scoped to a user.
 */
public interface BrokerCredentialFieldRepository extends JpaRepository<BrokerCredentialField, UUID> {

    /** The form for one broker. */
    List<BrokerCredentialField> findByBrokerIdOrderByFieldGroupAscDisplayOrderAscFieldKeyAsc(UUID brokerId);

    /** The same, minus retired descriptors - what the UI actually renders. */
    List<BrokerCredentialField> findByBrokerIdAndActiveOrderByFieldGroupAscDisplayOrderAscFieldKeyAsc(
            UUID brokerId, boolean active);

    /** By broker code, for a caller holding 'ZERODHA' rather than a uuid. */
    List<BrokerCredentialField> findByBrokerCodeIgnoreCaseOrderByFieldGroupAscDisplayOrderAscFieldKeyAsc(
            String brokerCode);

    List<BrokerCredentialField> findByBrokerCodeIgnoreCaseAndActiveOrderByFieldGroupAscDisplayOrderAscFieldKeyAsc(
            String brokerCode, boolean active);

    /** The business key: one descriptor per column, per broker. */
    Optional<BrokerCredentialField> findByBrokerIdAndFieldKeyIgnoreCase(UUID brokerId, String fieldKey);

    boolean existsByBrokerIdAndFieldKeyIgnoreCase(UUID brokerId, String fieldKey);

    /** Every form at once, for a UI that caches the whole catalog on load. */
    List<BrokerCredentialField> findAllByOrderByBrokerNameAscFieldGroupAscDisplayOrderAscFieldKeyAsc();
}

package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.BrokerRequest;
import com.example.tradeLedger.dto.BrokerResponse;
import com.example.tradeLedger.entity.Broker;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.BrokerRepository;
import com.example.tradeLedger.repository.UserBrokerRepository;
import com.example.tradeLedger.service.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class BrokerServiceImpl implements BrokerService {

    private static final Logger log = LoggerFactory.getLogger(BrokerServiceImpl.class);

    private static final int CODE_MAX = 30;
    private static final int NAME_MAX = 100;

    private static final Set<String> AUTH_TYPES =
            Set.of(Broker.AUTH_API_KEY, Broker.AUTH_OAUTH_REDIRECT, Broker.AUTH_TOTP);

    private final BrokerRepository brokerRepository;
    private final UserBrokerRepository userBrokerRepository;

    public BrokerServiceImpl(BrokerRepository brokerRepository,
                             UserBrokerRepository userBrokerRepository) {
        this.brokerRepository = brokerRepository;
        this.userBrokerRepository = userBrokerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrokerResponse> list(boolean activeOnly) {
        List<Broker> brokers = activeOnly
                ? brokerRepository.findByActiveTrueOrderByNameAsc()
                : brokerRepository.findAllByOrderByNameAsc();
        return brokers.stream().map(BrokerServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BrokerResponse get(UUID id) {
        return toResponse(brokerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Broker", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public BrokerResponse getByCode(String code) {
        String normalized = requireCode(code);
        return toResponse(brokerRepository.findByCodeIgnoreCase(normalized)
                .orElseThrow(() -> ResourceNotFoundException.of("Broker", normalized)));
    }

    @Override
    @Transactional
    public BrokerResponse create(BrokerRequest request) {
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        String code = requireCode(request.getCode());
        if (brokerRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new ResourceConflictException("Broker already exists: " + code);
        }

        Broker broker = new Broker();
        broker.setCode(code);
        broker.setName(requireName(request.getName()));
        broker.setDescription(trimToNull(request.getDescription()));
        broker.setApiBaseUrl(trimToNull(request.getApiBaseUrl()));
        broker.setAuthType(requireAuthType(request.getAuthType(), Broker.AUTH_API_KEY));
        broker.setActive(request.getActive() == null || request.getActive());
        broker = brokerRepository.save(broker);

        log.info("CREATE broker {} '{}' authType={}", code, broker.getName(), broker.getAuthType());
        return toResponse(broker);
    }

    @Override
    @Transactional
    public List<BrokerResponse> createAll(List<BrokerRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new StrategyValidationException("Send at least one broker");
        }

        // Duplicates inside the batch would otherwise fail halfway through on the
        // unique index, with the earlier rows already written. Cheaper to say so.
        Set<String> seen = new LinkedHashSet<>();
        for (BrokerRequest request : requests) {
            String code = requireCode(request == null ? null : request.getCode());
            if (!seen.add(code)) {
                throw new StrategyValidationException("Duplicate code in the batch: " + code);
            }
        }

        List<BrokerResponse> created = new ArrayList<>(requests.size());
        for (BrokerRequest request : requests) {
            created.add(create(request));
        }
        log.info("CREATE {} broker(s): {}", created.size(), seen);
        return created;
    }

    @Override
    @Transactional
    public BrokerResponse update(UUID id, BrokerRequest request) {
        Broker broker = brokerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Broker", id));
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        // The code is what adapters switch on and what every existing setup was
        // created against. Renaming it silently repoints all of them.
        if (request.getCode() != null && !requireCode(request.getCode()).equals(broker.getCode())) {
            throw new ResourceConflictException(
                    "A broker's code cannot change - adapters and existing setups are keyed on it. "
                            + "Deactivate " + broker.getCode() + " and create the new one instead.");
        }

        if (request.getName() != null) {
            broker.setName(requireName(request.getName()));
        }
        if (request.getDescription() != null) {
            broker.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getApiBaseUrl() != null) {
            broker.setApiBaseUrl(trimToNull(request.getApiBaseUrl()));
        }
        if (request.getAuthType() != null) {
            broker.setAuthType(requireAuthType(request.getAuthType(), broker.getAuthType()));
        }
        if (request.getActive() != null) {
            if (!request.getActive()) {
                long inUse = userBrokerRepository.countByBroker_Id(id);
                if (inUse > 0) {
                    // Allowed: it stops new setups without breaking existing ones.
                    log.warn("Deactivating broker {} with {} existing setup(s)", broker.getCode(), inUse);
                }
            }
            broker.setActive(request.getActive());
        }
        brokerRepository.save(broker);

        log.info("UPDATE broker {} active={}", broker.getCode(), broker.isActive());
        return toResponse(broker);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Broker broker = brokerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Broker", id));

        long inUse = userBrokerRepository.countByBroker_Id(id);
        if (inUse > 0) {
            throw new ResourceConflictException("Broker '" + broker.getCode() + "' is used by "
                    + inUse + " broker setup(s) across the platform. Deactivate it instead - "
                    + "that stops new setups without breaking the ones that exist.");
        }
        brokerRepository.delete(broker);
        log.info("DELETE broker {}", broker.getCode());
    }

    // -------------------------------------------------------------- helpers

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new StrategyValidationException("code is required");
        }
        String trimmed = code.trim().toUpperCase(Locale.ROOT);
        if (trimmed.length() > CODE_MAX) {
            throw new StrategyValidationException("code must be at most " + CODE_MAX + " characters");
        }
        if (!trimmed.matches("[A-Z0-9_]+")) {
            throw new StrategyValidationException(
                    "code must be letters, digits and underscores only - it is a handle, not a label");
        }
        return trimmed;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new StrategyValidationException("name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX) {
            throw new StrategyValidationException("name must be at most " + NAME_MAX + " characters");
        }
        return trimmed;
    }

    private static String requireAuthType(String authType, String fallback) {
        if (authType == null || authType.isBlank()) {
            return fallback;
        }
        String normalized = authType.trim().toLowerCase(Locale.ROOT);
        if (!AUTH_TYPES.contains(normalized)) {
            throw new StrategyValidationException(
                    "authType must be one of " + AUTH_TYPES + ", was: " + authType);
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BrokerResponse toResponse(Broker broker) {
        return new BrokerResponse(broker.getId(), broker.getCode(), broker.getName(),
                broker.getDescription(), broker.getApiBaseUrl(), broker.getAuthType(), broker.isActive());
    }
}

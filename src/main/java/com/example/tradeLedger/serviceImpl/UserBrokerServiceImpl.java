package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.UserBrokerRequest;
import com.example.tradeLedger.dto.UserBrokerResponse;
import com.example.tradeLedger.entity.Broker;
import com.example.tradeLedger.entity.BrokerCredential;
import com.example.tradeLedger.entity.User;
import com.example.tradeLedger.entity.UserBroker;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.BrokerCredentialRepository;
import com.example.tradeLedger.repository.BrokerRepository;
import com.example.tradeLedger.repository.TradingAccountRepository;
import com.example.tradeLedger.repository.UserBrokerRepository;
import com.example.tradeLedger.service.CurrentUserService;
import com.example.tradeLedger.service.UserBrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserBrokerServiceImpl implements UserBrokerService {

    private static final Logger log = LoggerFactory.getLogger(UserBrokerServiceImpl.class);

    private static final int LABEL_MAX = 100;

    private final CurrentUserService currentUserService;
    private final UserBrokerRepository userBrokerRepository;
    private final BrokerRepository brokerRepository;
    private final TradingAccountRepository tradingAccountRepository;
    private final BrokerCredentialRepository credentialRepository;

    public UserBrokerServiceImpl(CurrentUserService currentUserService,
                                 UserBrokerRepository userBrokerRepository,
                                 BrokerRepository brokerRepository,
                                 TradingAccountRepository tradingAccountRepository,
                                 BrokerCredentialRepository credentialRepository) {
        this.currentUserService = currentUserService;
        this.userBrokerRepository = userBrokerRepository;
        this.brokerRepository = brokerRepository;
        this.tradingAccountRepository = tradingAccountRepository;
        this.credentialRepository = credentialRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserBrokerResponse> list(String email, UUID brokerId, Boolean active) {
        User user = currentUserService.require(email);
        List<UserBroker> setups = brokerId != null
                ? userBrokerRepository.findByUser_IdAndBroker_IdOrderByLabelAsc(user.getId(), brokerId)
                : userBrokerRepository.findByUser_IdOrderByLabelAsc(user.getId());
        return setups.stream()
                .filter(s -> active == null || s.isActive() == active)
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserBrokerResponse get(String email, UUID id) {
        User user = currentUserService.require(email);
        return toResponse(requireOwned(user, id));
    }

    @Override
    @Transactional
    public UserBrokerResponse create(String email, UserBrokerRequest request) {
        User user = currentUserService.require(email);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        Broker broker = requireBroker(request);

        // Defaulting to the broker's own name makes the common case - one setup
        // per broker - a one-field request; a second Delta login has to be named.
        String label = normalizeLabel(request.getLabel(), broker.getName());
        if (userBrokerRepository.existsByUser_IdAndLabelIgnoreCase(user.getId(), label)) {
            throw new ResourceConflictException("You already have a broker setup called '" + label + "'");
        }

        UserBroker setup = new UserBroker();
        setup.setUser(user);
        setup.setBroker(broker);
        setup.setLabel(label);
        setup.setActive(request.getActive() == null || request.getActive());
        setup = userBrokerRepository.save(setup);

        log.info("CREATE broker setup '{}' id={} broker={} | user={}",
                label, setup.getId(), broker.getCode(), email);
        return toResponse(setup);
    }

    @Override
    @Transactional
    public UserBrokerResponse update(String email, UUID id, UserBrokerRequest request) {
        User user = currentUserService.require(email);
        UserBroker setup = requireOwned(user, id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        // Pointing an existing setup at a different broker would leave its key and
        // all its accounts attached to a broker that never issued them. That is a
        // new setup, not an edit.
        Broker requested = resolveBroker(request);
        if (requested != null && !requested.getId().equals(setup.getBroker().getId())) {
            throw new ResourceConflictException(
                    "A setup cannot change broker. Create a new setup for " + requested.getCode()
                            + " and move the accounts across.");
        }

        if (request.getLabel() != null) {
            String label = normalizeLabel(request.getLabel(), setup.getBroker().getName());
            if (!label.equalsIgnoreCase(setup.getLabel())
                    && userBrokerRepository.existsByUser_IdAndLabelIgnoreCase(user.getId(), label)) {
                throw new ResourceConflictException("You already have a broker setup called '" + label + "'");
            }
            setup.setLabel(label);
        }
        if (request.getActive() != null) {
            setup.setActive(request.getActive());
        }
        userBrokerRepository.save(setup);

        log.info("UPDATE broker setup={} active={} | user={}", id, setup.isActive(), email);
        return toResponse(setup);
    }

    @Override
    @Transactional
    public void delete(String email, UUID id) {
        User user = currentUserService.require(email);
        UserBroker setup = requireOwned(user, id);

        long accounts = tradingAccountRepository.countByUserBroker_Id(id);
        if (accounts > 0) {
            throw new ResourceConflictException("Broker setup '" + setup.getLabel() + "' still has "
                    + accounts + " trading account(s). Delete them first, or deactivate the setup instead.");
        }
        credentialRepository.findByUserBroker_Id(id).forEach(credentialRepository::delete);
        userBrokerRepository.delete(setup);
        log.info("DELETE broker setup={} | user={}", id, email);
    }

    // -------------------------------------------------------------- helpers

    private UserBroker requireOwned(User user, UUID id) {
        return userBrokerRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Broker setup", id));
    }

    /** Null when neither field was sent - the caller is not touching the broker. */
    private Broker resolveBroker(UserBrokerRequest request) {
        if (request.getBrokerId() != null) {
            return brokerRepository.findById(request.getBrokerId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Broker", request.getBrokerId()));
        }
        if (request.getBrokerCode() != null && !request.getBrokerCode().isBlank()) {
            String code = request.getBrokerCode().trim().toUpperCase(Locale.ROOT);
            return brokerRepository.findByCodeIgnoreCase(code)
                    .orElseThrow(() -> ResourceNotFoundException.of("Broker", code));
        }
        return null;
    }

    private Broker requireBroker(UserBrokerRequest request) {
        Broker broker = resolveBroker(request);
        if (broker == null) {
            throw new StrategyValidationException("brokerId or brokerCode is required");
        }
        if (!broker.isActive()) {
            throw new StrategyValidationException("Broker is not active: " + broker.getCode());
        }
        return broker;
    }

    private static String normalizeLabel(String submitted, String fallback) {
        String label = submitted == null || submitted.isBlank() ? fallback : submitted.trim();
        if (label == null || label.isBlank()) {
            throw new StrategyValidationException("label is required");
        }
        if (label.length() > LABEL_MAX) {
            throw new StrategyValidationException("label must be at most " + LABEL_MAX + " characters");
        }
        return label;
    }

    private UserBrokerResponse toResponse(UserBroker setup) {
        Broker broker = setup.getBroker();
        BrokerCredential own = credentialRepository
                .findByUserBroker_IdAndTradingAccountIsNull(setup.getId()).orElse(null);

        return new UserBrokerResponse(
                setup.getId(),
                broker.getId(),
                broker.getCode(),
                broker.getName(),
                broker.getAuthType(),
                setup.getLabel(),
                setup.isActive(),
                own != null && own.hasAnyValue(),
                tradingAccountRepository.countByUserBroker_Id(setup.getId()),
                credentialRepository.countByUserBroker_IdAndTradingAccountIsNotNull(setup.getId()),
                own != null ? own.getRotatedAt() : null,
                setup.getCreatedAt(),
                setup.getUpdatedAt());
    }
}

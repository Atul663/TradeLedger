package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.BrokerResponse;
import com.example.tradeLedger.dto.ExchangeResponse;
import com.example.tradeLedger.dto.RiskProfileResponse;
import com.example.tradeLedger.dto.SymbolResponse;
import com.example.tradeLedger.dto.UserRiskLimitRequest;
import com.example.tradeLedger.dto.UserRiskLimitResponse;
import com.example.tradeLedger.entity.Broker;
import com.example.tradeLedger.entity.Exchange;
import com.example.tradeLedger.entity.RiskProfile;
import com.example.tradeLedger.entity.Symbol;
import com.example.tradeLedger.entity.User;
import com.example.tradeLedger.entity.UserRiskLimit;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.BrokerRepository;
import com.example.tradeLedger.repository.ExchangeRepository;
import com.example.tradeLedger.repository.RiskProfileRepository;
import com.example.tradeLedger.repository.SymbolRepository;
import com.example.tradeLedger.repository.UserRiskLimitRepository;
import com.example.tradeLedger.service.CurrentUserService;
import com.example.tradeLedger.service.ReferenceDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ReferenceDataServiceImpl implements ReferenceDataService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataServiceImpl.class);

    private final ExchangeRepository exchangeRepository;
    private final BrokerRepository brokerRepository;
    private final SymbolRepository symbolRepository;
    private final RiskProfileRepository riskProfileRepository;
    private final UserRiskLimitRepository userRiskLimitRepository;
    private final CurrentUserService currentUserService;

    public ReferenceDataServiceImpl(ExchangeRepository exchangeRepository,
                                    BrokerRepository brokerRepository,
                                    SymbolRepository symbolRepository,
                                    RiskProfileRepository riskProfileRepository,
                                    UserRiskLimitRepository userRiskLimitRepository,
                                    CurrentUserService currentUserService) {
        this.exchangeRepository = exchangeRepository;
        this.brokerRepository = brokerRepository;
        this.symbolRepository = symbolRepository;
        this.riskProfileRepository = riskProfileRepository;
        this.userRiskLimitRepository = userRiskLimitRepository;
        this.currentUserService = currentUserService;
    }

    // ------------------------------------------------------------ exchanges

    @Override
    @Transactional(readOnly = true)
    public List<ExchangeResponse> listExchanges(String status) {
        List<Exchange> exchanges = status == null || status.isBlank()
                ? exchangeRepository.findAllByOrderByNameAsc()
                : exchangeRepository.findByStatusOrderByNameAsc(status.trim().toLowerCase(Locale.ROOT));
        return exchanges.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExchangeResponse getExchange(UUID id) {
        return toResponse(exchangeRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Exchange", id)));
    }

    // -------------------------------------------------------------- brokers

    @Override
    @Transactional(readOnly = true)
    public List<BrokerResponse> listBrokers(boolean activeOnly) {
        List<Broker> brokers = activeOnly
                ? brokerRepository.findByActiveTrueOrderByNameAsc()
                : brokerRepository.findAllByOrderByNameAsc();
        return brokers.stream().map(ReferenceDataServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BrokerResponse getBroker(UUID id) {
        return toResponse(brokerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Broker", id)));
    }

    // -------------------------------------------------------------- symbols

    @Override
    @Transactional(readOnly = true)
    public List<SymbolResponse> listSymbols(UUID exchangeId, boolean activeOnly) {
        List<Symbol> symbols;
        if (exchangeId != null) {
            symbols = activeOnly
                    ? symbolRepository.findByExchange_IdAndActiveTrueOrderBySymbolAsc(exchangeId)
                    : symbolRepository.findByExchange_IdOrderBySymbolAsc(exchangeId);
        } else {
            symbols = activeOnly
                    ? symbolRepository.findByActiveTrueOrderBySymbolAsc()
                    : symbolRepository.findAll();
        }
        return symbols.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SymbolResponse getSymbol(UUID id) {
        return toResponse(symbolRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Symbol", id)));
    }

    // -------------------------------------------------------- risk profiles

    @Override
    @Transactional(readOnly = true)
    public List<RiskProfileResponse> listRiskProfiles() {
        return riskProfileRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RiskProfileResponse getRiskProfile(UUID id) {
        return toResponse(riskProfileRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Risk profile", id)));
    }

    // ---------------------------------------------------------- user limits

    @Override
    @Transactional(readOnly = true)
    public UserRiskLimitResponse getRiskLimits(String email) {
        User user = currentUserService.require(email);
        return userRiskLimitRepository.findById(user.getId())
                .map(this::toResponse)
                .orElseGet(() -> new UserRiskLimitResponse(user.getId(), null, null, null, null));
    }

    @Override
    @Transactional
    public UserRiskLimitResponse setRiskLimits(String email, UserRiskLimitRequest request) {
        User user = currentUserService.require(email);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        List<String> errors = new ArrayList<>();
        if (request.getMaxDailyLoss() != null && request.getMaxDailyLoss().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("maxDailyLoss must not be negative");
        }
        if (request.getMaxTotalExposure() != null
                && request.getMaxTotalExposure().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("maxTotalExposure must not be negative");
        }
        if (request.getMaxOpenPositions() != null && request.getMaxOpenPositions() < 0) {
            errors.add("maxOpenPositions must not be negative");
        }
        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }

        UserRiskLimit limits = userRiskLimitRepository.findById(user.getId()).orElseGet(() -> {
            UserRiskLimit fresh = new UserRiskLimit();
            fresh.setUserId(user.getId());
            return fresh;
        });
        limits.setMaxDailyLoss(request.getMaxDailyLoss());
        limits.setMaxOpenPositions(request.getMaxOpenPositions());
        limits.setMaxTotalExposure(request.getMaxTotalExposure());
        userRiskLimitRepository.save(limits);

        log.info("SET risk limits dailyLoss={} openPositions={} exposure={} | user={}",
                limits.getMaxDailyLoss(), limits.getMaxOpenPositions(), limits.getMaxTotalExposure(), email);
        return toResponse(limits);
    }

    // -------------------------------------------------------------- mapping

    private static BrokerResponse toResponse(Broker broker) {
        return new BrokerResponse(broker.getId(), broker.getCode(), broker.getName(),
                broker.getDescription(), broker.getApiBaseUrl(), broker.getAuthType(), broker.isActive());
    }

    private ExchangeResponse toResponse(Exchange exchange) {
        return new ExchangeResponse(exchange.getId(), exchange.getName(), exchange.getCode(),
                exchange.getDescription(), exchange.getStatus());
    }

    private SymbolResponse toResponse(Symbol symbol) {
        Exchange exchange = symbol.getExchange();
        return new SymbolResponse(
                symbol.getId(),
                exchange.getId(),
                exchange.getCode(),
                symbol.getSymbol(),
                symbol.getBaseAsset(),
                symbol.getQuoteAsset(),
                symbol.getInstrumentType(),
                symbol.getOptionType(),
                symbol.getStrikePrice(),
                symbol.getExpiryAt(),
                symbol.getContractSize(),
                symbol.getTickSize(),
                symbol.getMinQty(),
                symbol.isActive());
    }

    private RiskProfileResponse toResponse(RiskProfile profile) {
        return new RiskProfileResponse(
                profile.getId(),
                profile.getName(),
                profile.getDescription(),
                profile.getMaxDailyLoss(),
                profile.getMaxDrawdown(),
                profile.getMaxPositionSize(),
                profile.getMaxTotalExposure(),
                profile.getMaxTradesPerDay(),
                profile.isKillSwitchEnabled());
    }

    private UserRiskLimitResponse toResponse(UserRiskLimit limits) {
        return new UserRiskLimitResponse(limits.getUserId(), limits.getMaxDailyLoss(),
                limits.getMaxOpenPositions(), limits.getMaxTotalExposure(), limits.getUpdatedAt());
    }
}

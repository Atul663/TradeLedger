package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.ExchangeRequest;
import com.example.tradeLedger.dto.ExchangeResponse;
import com.example.tradeLedger.dto.RiskProfileRequest;
import com.example.tradeLedger.dto.RiskProfileResponse;
import com.example.tradeLedger.dto.SymbolRequest;
import com.example.tradeLedger.dto.SymbolResponse;
import com.example.tradeLedger.dto.UserRiskLimitRequest;
import com.example.tradeLedger.dto.UserRiskLimitResponse;
import com.example.tradeLedger.entity.Exchange;
import com.example.tradeLedger.entity.RiskProfile;
import com.example.tradeLedger.entity.Symbol;
import com.example.tradeLedger.entity.User;
import com.example.tradeLedger.entity.UserRiskLimit;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.ExchangeRepository;
import com.example.tradeLedger.repository.RiskProfileRepository;
import com.example.tradeLedger.repository.StrategySubscriptionRepository;
import com.example.tradeLedger.repository.SymbolRepository;
import com.example.tradeLedger.repository.UserStrategyRepository;
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
import java.util.Set;
import java.util.UUID;

@Service
public class ReferenceDataServiceImpl implements ReferenceDataService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataServiceImpl.class);

    private static final Set<String> EXCHANGE_STATUSES =
            Set.of(Exchange.STATUS_ACTIVE, Exchange.STATUS_DISABLED);

    private static final Set<String> INSTRUMENT_TYPES = Set.of(
            Symbol.TYPE_SPOT, Symbol.TYPE_FUTURE, Symbol.TYPE_OPTION, Symbol.TYPE_INDEX);

    private static final Set<String> OPTION_TYPES =
            Set.of(Symbol.OPTION_CALL, Symbol.OPTION_PUT);

    private final ExchangeRepository exchangeRepository;
    private final SymbolRepository symbolRepository;
    private final RiskProfileRepository riskProfileRepository;
    private final UserRiskLimitRepository userRiskLimitRepository;
    private final UserStrategyRepository userStrategyRepository;
    private final StrategySubscriptionRepository subscriptionRepository;
    private final CurrentUserService currentUserService;

    public ReferenceDataServiceImpl(ExchangeRepository exchangeRepository,
                                    SymbolRepository symbolRepository,
                                    RiskProfileRepository riskProfileRepository,
                                    UserRiskLimitRepository userRiskLimitRepository,
                                    UserStrategyRepository userStrategyRepository,
                                    StrategySubscriptionRepository subscriptionRepository,
                                    CurrentUserService currentUserService) {
        this.exchangeRepository = exchangeRepository;
        this.symbolRepository = symbolRepository;
        this.riskProfileRepository = riskProfileRepository;
        this.userRiskLimitRepository = userRiskLimitRepository;
        this.userStrategyRepository = userStrategyRepository;
        this.subscriptionRepository = subscriptionRepository;
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
        return toResponse(requireExchange(id));
    }

    @Override
    @Transactional
    public ExchangeResponse createExchange(ExchangeRequest request) {
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        List<String> errors = new ArrayList<>();
        String name = trimToNull(request.getName());
        String code = normalizeCode(request.getCode());

        if (name == null) {
            errors.add("name is required");
        } else if (name.length() > 50) {
            errors.add("name must be at most 50 characters");
        }
        if (code == null) {
            errors.add("code is required");
        } else if (code.length() > 20) {
            errors.add("code must be at most 20 characters");
        }
        String status = normalizeExchangeStatus(request.getStatus(), errors);
        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }
        if (exchangeRepository.existsByCode(code)) {
            throw new ResourceConflictException("Exchange already exists: " + code);
        }
        if (exchangeRepository.existsByName(name)) {
            throw new ResourceConflictException("An exchange is already named: " + name);
        }

        Exchange exchange = new Exchange();
        exchange.setName(name);
        exchange.setCode(code);
        exchange.setDescription(trimToNull(request.getDescription()));
        exchange.setStatus(status != null ? status : Exchange.STATUS_ACTIVE);
        exchangeRepository.save(exchange);

        log.info("CREATE exchange {} ({}) id={}", code, exchange.getStatus(), exchange.getId());
        return toResponse(exchange);
    }

    @Override
    @Transactional
    public ExchangeResponse updateExchange(UUID id, ExchangeRequest request) {
        Exchange exchange = requireExchange(id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        List<String> errors = new ArrayList<>();
        String name = trimToNull(request.getName());
        String code = normalizeCode(request.getCode());
        String status = normalizeExchangeStatus(request.getStatus(), errors);
        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }

        if (name != null && !name.equals(exchange.getName())) {
            if (exchangeRepository.existsByName(name)) {
                throw new ResourceConflictException("An exchange is already named: " + name);
            }
            exchange.setName(name);
        }
        if (code != null && !code.equals(exchange.getCode())) {
            // The code is what a strategy sends as exchangeCode, so moving it under
            // live symbols would silently break every client resolving by name.
            long symbols = symbolRepository.countByExchange_Id(id);
            if (symbols > 0) {
                throw new ResourceConflictException("Exchange " + exchange.getCode() + " has "
                        + symbols + " symbol(s); its code is what clients resolve them by and "
                        + "cannot change underneath them.");
            }
            if (exchangeRepository.existsByCode(code)) {
                throw new ResourceConflictException("Exchange already exists: " + code);
            }
            exchange.setCode(code);
        }
        if (request.getDescription() != null) {
            exchange.setDescription(trimToNull(request.getDescription()));
        }
        if (status != null) {
            exchange.setStatus(status);
        }
        exchangeRepository.save(exchange);

        log.info("UPDATE exchange {} status={} id={}", exchange.getCode(), exchange.getStatus(), id);
        return toResponse(exchange);
    }

    @Override
    @Transactional
    public void deleteExchange(UUID id) {
        Exchange exchange = requireExchange(id);
        long symbols = symbolRepository.countByExchange_Id(id);
        if (symbols > 0) {
            throw new ResourceConflictException("Exchange " + exchange.getCode() + " still has "
                    + symbols + " symbol(s) and cannot be deleted. Disable it instead "
                    + "(PUT /api/v1/exchanges/" + id + " with status disabled).");
        }
        exchangeRepository.delete(exchange);
        log.info("DELETE exchange {} id={}", exchange.getCode(), id);
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
        return toResponse(requireSymbol(id));
    }

    @Override
    @Transactional
    public SymbolResponse createSymbol(SymbolRequest request) {
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        Exchange exchange = resolveExchange(request);
        List<String> errors = new ArrayList<>();

        String ticker = normalizeCode(request.getSymbol());
        if (ticker == null) {
            errors.add("symbol is required");
        } else if (ticker.length() > 50) {
            errors.add("symbol must be at most 50 characters");
        }
        String instrumentType = normalizeInstrumentType(request.getInstrumentType(), errors);
        if (instrumentType == null && errors.isEmpty()) {
            errors.add("instrumentType is required (" + INSTRUMENT_TYPES + ")");
        }
        String optionType = normalizeOptionType(instrumentType, request.getOptionType(),
                request.getStrikePrice(), errors);
        checkMeasures(request, errors);
        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }
        symbolRepository.findByExchange_IdAndSymbol(exchange.getId(), ticker).ifPresent(existing -> {
            throw new ResourceConflictException("Symbol already exists on " + exchange.getCode()
                    + ": " + existing.getSymbol() + " (id=" + existing.getId() + ")");
        });

        Symbol symbol = new Symbol();
        symbol.setExchange(exchange);
        symbol.setSymbol(ticker);
        symbol.setInstrumentType(instrumentType);
        symbol.setOptionType(optionType);
        symbol.setStrikePrice(request.getStrikePrice());
        symbol.setExpiryAt(request.getExpiryAt());
        symbol.setBaseAsset(trimToNull(request.getBaseAsset()));
        symbol.setQuoteAsset(trimToNull(request.getQuoteAsset()));
        symbol.setContractSize(request.getContractSize());
        symbol.setTickSize(request.getTickSize());
        symbol.setMinQty(request.getMinQty());
        symbol.setActive(request.getActive() == null || request.getActive());
        symbolRepository.save(symbol);

        log.info("CREATE symbol {}:{} type={} id={}",
                exchange.getCode(), ticker, instrumentType, symbol.getId());
        return toResponse(symbol);
    }

    @Override
    @Transactional
    public SymbolResponse updateSymbol(UUID id, SymbolRequest request) {
        Symbol symbol = requireSymbol(id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        List<String> errors = new ArrayList<>();

        // The venue is fixed: symbols are unique per exchange and a strategy's
        // FK points at this row, so moving it would silently retarget every
        // strategy watching it.
        if (request.getExchangeId() != null || request.getExchangeCode() != null) {
            Exchange requested = resolveExchange(request);
            if (!requested.getId().equals(symbol.getExchange().getId())) {
                errors.add("A symbol cannot move between exchanges - create it on "
                        + requested.getCode() + " instead");
            }
        }

        String ticker = normalizeCode(request.getSymbol());
        String instrumentType = request.getInstrumentType() == null
                ? symbol.getInstrumentType()
                : normalizeInstrumentType(request.getInstrumentType(), errors);

        // Judged against the resulting row, not the submitted fragment: clearing
        // instrumentType to option without an optionType has to fail even when the
        // optionType was set in an earlier request.
        String optionTypeInput = request.getOptionType() != null
                ? request.getOptionType()
                : symbol.getOptionType();
        java.math.BigDecimal strike = request.getStrikePrice() != null
                ? request.getStrikePrice()
                : symbol.getStrikePrice();
        String optionType = normalizeOptionType(instrumentType, optionTypeInput, strike, errors);
        checkMeasures(request, errors);
        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }

        if (ticker != null && !ticker.equals(symbol.getSymbol())) {
            symbolRepository.findByExchange_IdAndSymbol(symbol.getExchange().getId(), ticker)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ResourceConflictException("Symbol already exists on "
                                + symbol.getExchange().getCode() + ": " + ticker);
                    });
            symbol.setSymbol(ticker);
        }
        symbol.setInstrumentType(instrumentType);
        symbol.setOptionType(optionType);
        if (request.getStrikePrice() != null) {
            symbol.setStrikePrice(request.getStrikePrice());
        }
        if (request.getExpiryAt() != null) {
            symbol.setExpiryAt(request.getExpiryAt());
        }
        if (request.getBaseAsset() != null) {
            symbol.setBaseAsset(trimToNull(request.getBaseAsset()));
        }
        if (request.getQuoteAsset() != null) {
            symbol.setQuoteAsset(trimToNull(request.getQuoteAsset()));
        }
        if (request.getContractSize() != null) {
            symbol.setContractSize(request.getContractSize());
        }
        if (request.getTickSize() != null) {
            symbol.setTickSize(request.getTickSize());
        }
        if (request.getMinQty() != null) {
            symbol.setMinQty(request.getMinQty());
        }
        if (request.getActive() != null) {
            symbol.setActive(request.getActive());
        }
        symbolRepository.save(symbol);

        log.info("UPDATE symbol {}:{} active={} id={}",
                symbol.getExchange().getCode(), symbol.getSymbol(), symbol.isActive(), id);
        return toResponse(symbol);
    }

    @Override
    @Transactional
    public void deleteSymbol(UUID id) {
        Symbol symbol = requireSymbol(id);
        long strategies = userStrategyRepository.countBySymbol_Id(id);
        if (strategies > 0) {
            throw new ResourceConflictException("Symbol " + symbol.getSymbol() + " is watched by "
                    + strategies + " strategy(ies) and cannot be deleted. Deactivate it instead "
                    + "(PUT /api/v1/symbols/" + id + " with active false).");
        }
        symbolRepository.delete(symbol);
        log.info("DELETE symbol {}:{} id={}", symbol.getExchange().getCode(), symbol.getSymbol(), id);
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
        return toResponse(requireRiskProfile(id));
    }

    @Override
    @Transactional
    public RiskProfileResponse createRiskProfile(RiskProfileRequest request) {
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        List<String> errors = new ArrayList<>();
        String name = trimToNull(request.getName());
        if (name == null) {
            errors.add("name is required");
        } else if (name.length() > 100) {
            errors.add("name must be at most 100 characters");
        }
        checkCaps(request, errors);
        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }

        RiskProfile profile = new RiskProfile();
        profile.setName(name);
        profile.setDescription(trimToNull(request.getDescription()));
        profile.setMaxDailyLoss(request.getMaxDailyLoss());
        profile.setMaxDrawdown(request.getMaxDrawdown());
        profile.setMaxPositionSize(request.getMaxPositionSize());
        profile.setMaxTotalExposure(request.getMaxTotalExposure());
        profile.setMaxTradesPerDay(request.getMaxTradesPerDay());
        profile.setKillSwitchEnabled(request.getKillSwitchEnabled() == null
                || request.getKillSwitchEnabled());
        riskProfileRepository.save(profile);

        log.info("CREATE risk profile {} id={}", name, profile.getId());
        return toResponse(profile);
    }

    @Override
    @Transactional
    public RiskProfileResponse updateRiskProfile(UUID id, RiskProfileRequest request) {
        RiskProfile profile = requireRiskProfile(id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        List<String> errors = new ArrayList<>();
        String name = trimToNull(request.getName());
        if (name != null && name.length() > 100) {
            errors.add("name must be at most 100 characters");
        }
        checkCaps(request, errors);
        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }

        if (name != null) {
            profile.setName(name);
        }
        if (request.getDescription() != null) {
            profile.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getMaxDailyLoss() != null) {
            profile.setMaxDailyLoss(request.getMaxDailyLoss());
        }
        if (request.getMaxDrawdown() != null) {
            profile.setMaxDrawdown(request.getMaxDrawdown());
        }
        if (request.getMaxPositionSize() != null) {
            profile.setMaxPositionSize(request.getMaxPositionSize());
        }
        if (request.getMaxTotalExposure() != null) {
            profile.setMaxTotalExposure(request.getMaxTotalExposure());
        }
        if (request.getMaxTradesPerDay() != null) {
            profile.setMaxTradesPerDay(request.getMaxTradesPerDay());
        }
        if (request.getKillSwitchEnabled() != null) {
            profile.setKillSwitchEnabled(request.getKillSwitchEnabled());
        }
        riskProfileRepository.save(profile);

        log.info("UPDATE risk profile {} id={}", profile.getName(), id);
        return toResponse(profile);
    }

    @Override
    @Transactional
    public void deleteRiskProfile(UUID id) {
        RiskProfile profile = requireRiskProfile(id);
        // A deployment's FK points here; deleting under it would fail at the
        // constraint anyway, so refuse with something the caller can act on.
        if (subscriptionRepository.existsByRiskProfile_Id(id)) {
            throw new ResourceConflictException("Risk profile " + profile.getName()
                    + " is in use by at least one deployment and cannot be deleted. "
                    + "Point those deployments at another profile first.");
        }
        riskProfileRepository.delete(profile);
        log.info("DELETE risk profile {} id={}", profile.getName(), id);
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

    // ------------------------------------------------------------ resolving

    private Exchange requireExchange(UUID id) {
        return exchangeRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Exchange", id));
    }

    private Symbol requireSymbol(UUID id) {
        return symbolRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Symbol", id));
    }

    private RiskProfile requireRiskProfile(UUID id) {
        return riskProfileRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Risk profile", id));
    }

    /** By id, or by code - the same two ways a strategy names its market. */
    private Exchange resolveExchange(SymbolRequest request) {
        if (request.getExchangeId() != null) {
            return requireExchange(request.getExchangeId());
        }
        String code = normalizeCode(request.getExchangeCode());
        if (code == null) {
            throw new StrategyValidationException("exchangeId, or exchangeCode, is required");
        }
        return exchangeRepository.findByCode(code)
                .orElseThrow(() -> ResourceNotFoundException.of("Exchange", code));
    }

    // ----------------------------------------------------------- validation

    private String normalizeExchangeStatus(String status, List<String> errors) {
        String normalized = status == null || status.isBlank()
                ? null
                : status.trim().toLowerCase(Locale.ROOT);
        if (normalized != null && !EXCHANGE_STATUSES.contains(normalized)) {
            errors.add("status must be one of " + EXCHANGE_STATUSES + ", got " + status);
            return null;
        }
        return normalized;
    }

    private String normalizeInstrumentType(String instrumentType, List<String> errors) {
        String normalized = instrumentType == null || instrumentType.isBlank()
                ? null
                : instrumentType.trim().toLowerCase(Locale.ROOT);
        if (normalized != null && !INSTRUMENT_TYPES.contains(normalized)) {
            errors.add("instrumentType must be one of " + INSTRUMENT_TYPES + ", got " + instrumentType);
            return null;
        }
        return normalized;
    }

    /**
     * An option needs a side and a strike; anything else must not carry them.
     *
     * Checked against the RESULTING row rather than the submitted fragment, so a
     * partial update cannot leave an option with no side by changing only the
     * instrument type.
     */
    private String normalizeOptionType(String instrumentType, String optionType,
                                       BigDecimal strikePrice, List<String> errors) {
        boolean isOption = Symbol.TYPE_OPTION.equals(instrumentType);
        String normalized = optionType == null || optionType.isBlank()
                ? null
                : optionType.trim().toUpperCase(Locale.ROOT);

        if (normalized != null && !OPTION_TYPES.contains(normalized)) {
            errors.add("optionType must be one of " + OPTION_TYPES + ", got " + optionType);
            return null;
        }
        if (isOption) {
            if (normalized == null) {
                errors.add("optionType is required when instrumentType is option (CALL or PUT)");
            }
            if (strikePrice == null) {
                errors.add("strikePrice is required when instrumentType is option");
            }
        } else if (normalized != null) {
            errors.add("optionType only applies when instrumentType is option, not "
                    + instrumentType);
            return null;
        }
        return normalized;
    }

    private void checkMeasures(SymbolRequest request, List<String> errors) {
        requirePositive(errors, "contractSize", request.getContractSize());
        requirePositive(errors, "tickSize", request.getTickSize());
        requirePositive(errors, "minQty", request.getMinQty());
        requirePositive(errors, "strikePrice", request.getStrikePrice());
    }

    private void checkCaps(RiskProfileRequest request, List<String> errors) {
        requireNonNegative(errors, "maxDailyLoss", request.getMaxDailyLoss());
        requireNonNegative(errors, "maxDrawdown", request.getMaxDrawdown());
        requireNonNegative(errors, "maxPositionSize", request.getMaxPositionSize());
        requireNonNegative(errors, "maxTotalExposure", request.getMaxTotalExposure());
        if (request.getMaxTradesPerDay() != null && request.getMaxTradesPerDay() < 1) {
            errors.add("maxTradesPerDay must be at least 1, got " + request.getMaxTradesPerDay());
        }
    }

    private void requirePositive(List<String> errors, String field, BigDecimal value) {
        if (value != null && value.signum() <= 0) {
            errors.add(field + " must be greater than 0, got " + value.toPlainString());
        }
    }

    private void requireNonNegative(List<String> errors, String field, BigDecimal value) {
        if (value != null && value.signum() < 0) {
            errors.add(field + " must not be negative, got " + value.toPlainString());
        }
    }

    /** Codes and tickers are matched by exact string, so they are normalized on write. */
    private String normalizeCode(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // -------------------------------------------------------------- mapping

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

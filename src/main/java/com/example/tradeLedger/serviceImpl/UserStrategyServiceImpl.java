package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.EffectiveParameterResponse;
import com.example.tradeLedger.dto.ParameterOverrideRequest;
import com.example.tradeLedger.dto.StrategySubscriptionRequest;
import com.example.tradeLedger.dto.StrategySubscriptionResponse;
import com.example.tradeLedger.dto.UserStrategyIndicatorResponse;
import com.example.tradeLedger.dto.UserStrategyRequest;
import com.example.tradeLedger.dto.UserStrategyResponse;
import com.example.tradeLedger.dto.UserStrategyRuntimeResponse;
import com.example.tradeLedger.dto.UserStrategySubscribeRequest;
import com.example.tradeLedger.dto.UserStrategyUpdateRequest;
import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.IndicatorParameterLink;
import com.example.tradeLedger.entity.Parameter;
import com.example.tradeLedger.entity.StrategyIndicatorLink;
import com.example.tradeLedger.entity.StrategyParameterLink;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.entity.Symbol;
import com.example.tradeLedger.entity.User;
import com.example.tradeLedger.entity.UserStrategy;
import com.example.tradeLedger.entity.UserStrategyIndicator;
import com.example.tradeLedger.entity.UserStrategyParameter;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.IndicatorParameterLinkRepository;
import com.example.tradeLedger.repository.ParameterRepository;
import com.example.tradeLedger.repository.StrategyIndicatorLinkRepository;
import com.example.tradeLedger.repository.StrategyParamDefinitionRepository;
import com.example.tradeLedger.repository.StrategyParameterLinkRepository;
import com.example.tradeLedger.repository.StrategyTemplateRepository;
import com.example.tradeLedger.repository.UserStrategyIndicatorRepository;
import com.example.tradeLedger.repository.UserStrategyParameterRepository;
import com.example.tradeLedger.repository.UserStrategyRepository;
import com.example.tradeLedger.service.CurrentUserService;
import com.example.tradeLedger.service.StrategySubscriptionService;
import com.example.tradeLedger.service.UserStrategyService;
import com.example.tradeLedger.serviceImpl.StrategyParamValidator.ValidatedParams;
import com.example.tradeLedger.utils.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserStrategyServiceImpl implements UserStrategyService {

    private static final Logger log = LoggerFactory.getLogger(UserStrategyServiceImpl.class);

    private static final int NAME_MAX = 100;

    private final CurrentUserService currentUserService;
    private final UserStrategyRepository userStrategyRepository;
    private final UserStrategyIndicatorRepository indicatorRowRepository;
    private final UserStrategyParameterRepository overrideRepository;
    private final StrategyTemplateRepository templateRepository;
    private final StrategyIndicatorLinkRepository strategyIndicatorRepository;
    private final IndicatorParameterLinkRepository indicatorParameterRepository;
    private final StrategyParameterLinkRepository strategyParameterRepository;
    private final StrategyParamDefinitionRepository paramDefRepository;
    private final ParameterRepository parameterRepository;
    private final StrategySubscriptionService subscriptionService;
    private final StrategyParamValidator paramValidator;
    private final SymbolResolver symbolResolver;
    private final JsonSupport json;

    public UserStrategyServiceImpl(CurrentUserService currentUserService,
                                   UserStrategyRepository userStrategyRepository,
                                   UserStrategyIndicatorRepository indicatorRowRepository,
                                   UserStrategyParameterRepository overrideRepository,
                                   StrategyTemplateRepository templateRepository,
                                   StrategyIndicatorLinkRepository strategyIndicatorRepository,
                                   IndicatorParameterLinkRepository indicatorParameterRepository,
                                   StrategyParameterLinkRepository strategyParameterRepository,
                                   StrategyParamDefinitionRepository paramDefRepository,
                                   ParameterRepository parameterRepository,
                                   StrategySubscriptionService subscriptionService,
                                   StrategyParamValidator paramValidator,
                                   SymbolResolver symbolResolver,
                                   JsonSupport json) {
        this.currentUserService = currentUserService;
        this.userStrategyRepository = userStrategyRepository;
        this.indicatorRowRepository = indicatorRowRepository;
        this.overrideRepository = overrideRepository;
        this.templateRepository = templateRepository;
        this.strategyIndicatorRepository = strategyIndicatorRepository;
        this.indicatorParameterRepository = indicatorParameterRepository;
        this.strategyParameterRepository = strategyParameterRepository;
        this.paramDefRepository = paramDefRepository;
        this.parameterRepository = parameterRepository;
        this.subscriptionService = subscriptionService;
        this.paramValidator = paramValidator;
        this.symbolResolver = symbolResolver;
        this.json = json;
    }

    // ----------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public List<UserStrategyResponse> list(String email, Boolean active, UUID strategyId) {
        User user = currentUserService.require(email);

        List<UserStrategy> rows;
        if (strategyId != null) {
            rows = userStrategyRepository.findByUser_IdAndStrategy_IdOrderByCreatedAtAsc(user.getId(), strategyId);
        } else if (active != null) {
            rows = userStrategyRepository.findByUser_IdAndActiveOrderByCreatedAtAsc(user.getId(), active);
        } else {
            rows = userStrategyRepository.findByUser_IdOrderByCreatedAtAsc(user.getId());
        }
        // The strategyId branch cannot express both filters in one derived query,
        // so the archive flag is applied here rather than growing a third method.
        return rows.stream()
                .filter(row -> active == null || row.isActive() == active)
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserStrategyResponse get(String email, UUID id) {
        User user = currentUserService.require(email);
        return toResponse(requireOwned(user, id));
    }

    @Override
    @Transactional(readOnly = true)
    public UserStrategyRuntimeResponse runtime(String email, UUID id) {
        User user = currentUserService.require(email);
        UserStrategy userStrategy = requireOwned(user, id);
        StrategyTemplate template = userStrategy.getStrategy();

        // Validated rather than raw: the same coercion the engine applies, so the
        // bot gets 9 as a number and 5m as a timeframe, not whatever text happens
        // to sit in custom_value.
        ValidatedParams params = validateEffective(userStrategy);

        List<UserStrategyRuntimeResponse.Indicator> indicators = new ArrayList<>();
        for (UserStrategyIndicator row : indicatorRows(userStrategy)) {
            if (!row.isEnabled()) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (EffectiveParameterResponse knob : indicatorKnobs(userStrategy, row)) {
                values.put(knob.code(), knob.effectiveValue());
            }
            indicators.add(new UserStrategyRuntimeResponse.Indicator(
                    row.getIndicator().getId(), row.getIndicator().getName(), row.getSlot(), values));
        }

        Symbol symbol = userStrategy.getSymbol();
        return new UserStrategyRuntimeResponse(
                userStrategy.getId(),
                userStrategy.getUser().getId(),
                template.getId(),
                template.getName(),
                template.getRuleTree(),
                symbol != null ? symbol.getId() : null,
                symbol != null ? symbol.getSymbol() : null,
                userStrategy.getTimeframe(),
                userStrategy.isActive(),
                indicators,
                params.getSignal(),
                params.getExecution());
    }

    // --------------------------------------------------------------- create

    @Override
    @Transactional
    public UserStrategyResponse create(String email, UserStrategyRequest request) {
        User user = currentUserService.require(email);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        StrategyTemplate template = resolveTemplate(request.getStrategyId(), request.getStrategyName());
        if (!template.isActive()) {
            throw new StrategyValidationException("Strategy template is not active: " + template.getName());
        }

        // Defaulting to the template's own name makes "save this as-is" a one-field
        // request; the second customization of the same template has to be named.
        String name = normalizeName(request.getName(), template.getName());
        requireNameFree(user, name, null);

        UserStrategy userStrategy = new UserStrategy();
        userStrategy.setUser(user);
        userStrategy.setStrategy(template);
        userStrategy.setName(name);
        userStrategy.setDescription(trimToNull(request.getDescription()));
        userStrategy.setSymbol(symbolResolver.resolveOrNull(
                request.getSymbolId(), request.getSymbol(), request.getExchangeCode()));
        userStrategy.setTimeframe(Timeframes.normalizeOrNull(request.getTimeframe()));
        userStrategy.setActive(true);
        userStrategyRepository.save(userStrategy);

        seedIndicatorRows(userStrategy, template);
        applyOverrides(userStrategy, request.getOverrides());
        validateEffective(userStrategy);

        log.info("CREATE user strategy '{}' from template '{}' overrides={} | user={}",
                name, template.getName(),
                request.getOverrides() == null ? 0 : request.getOverrides().size(), email);

        return toResponse(userStrategy);
    }

    /**
     * Mirrors the template's indicator set onto the user strategy, one row per
     * indicator the template declares.
     *
     * Read from {@code strategy_indicator_links} but stored as a foreign key to
     * {@code indicators}: that index is rebuilt from the rule tree on every
     * template save, and user rows must not depend on a table that regenerates.
     */
    private void seedIndicatorRows(UserStrategy userStrategy, StrategyTemplate template) {
        List<StrategyIndicatorLink> links =
                strategyIndicatorRepository.findByStrategy_IdOrderByIndicator_NameAsc(template.getId());
        int order = 0;
        for (StrategyIndicatorLink link : links) {
            UserStrategyIndicator row = new UserStrategyIndicator();
            row.setUserStrategy(userStrategy);
            row.setIndicator(link.getIndicator());
            row.setEnabled(true);
            row.setDisplayOrder(order++);
            indicatorRowRepository.save(row);
            userStrategy.getIndicators().add(row);
        }
    }

    // --------------------------------------------------------------- update

    @Override
    @Transactional
    public UserStrategyResponse update(String email, UUID id, UserStrategyUpdateRequest request) {
        User user = currentUserService.require(email);
        UserStrategy userStrategy = requireOwned(user, id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        if (request.getName() != null) {
            String name = normalizeName(request.getName(), userStrategy.getStrategy().getName());
            requireNameFree(user, name, userStrategy.getId());
            userStrategy.setName(name);
        }
        if (request.getDescription() != null) {
            userStrategy.setDescription(trimToNull(request.getDescription()));
        }
        Symbol symbol = symbolResolver.resolveOrNull(
                request.getSymbolId(), request.getSymbol(), request.getExchangeCode());
        if (symbol != null) {
            userStrategy.setSymbol(symbol);
        }
        String timeframe = Timeframes.normalizeOrNull(request.getTimeframe());
        if (timeframe != null) {
            userStrategy.setTimeframe(timeframe);
        }
        if (request.getActive() != null) {
            userStrategy.setActive(request.getActive());
        }

        if (request.getIndicators() != null) {
            for (UserStrategyUpdateRequest.IndicatorToggle toggle : request.getIndicators()) {
                if (toggle == null || toggle.getEnabled() == null) {
                    continue;
                }
                UserStrategyIndicator row = resolveIndicatorRow(userStrategy,
                        toggle.getUserStrategyIndicatorId(), toggle.getIndicatorId(), toggle.getSlot());
                row.setEnabled(toggle.getEnabled());
                indicatorRowRepository.save(row);
            }
        }

        applyOverrides(userStrategy, request.getOverrides());
        userStrategyRepository.save(userStrategy);
        validateEffective(userStrategy);

        log.info("UPDATE user strategy={} overrides={} | user={}",
                id, request.getOverrides() == null ? 0 : request.getOverrides().size(), email);

        return toResponse(userStrategy);
    }

    // --------------------------------------------------------------- delete

    @Override
    @Transactional
    public void delete(String email, UUID id) {
        User user = currentUserService.require(email);
        UserStrategy userStrategy = requireOwned(user, id);
        // The indicator rows and overrides cascade; nothing global is touched, and
        // a subscription already made from this row keeps running.
        userStrategyRepository.delete(userStrategy);
        log.info("DELETE user strategy={} '{}' | user={}", id, userStrategy.getName(), email);
    }

    // ------------------------------------------------------------ subscribe

    @Override
    @Transactional
    public StrategySubscriptionResponse subscribe(String email, UUID id,
                                                  UserStrategySubscribeRequest request) {
        User user = currentUserService.require(email);
        UserStrategy userStrategy = requireOwned(user, id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        if (!userStrategy.isActive()) {
            throw new StrategyValidationException(
                    "User strategy '" + userStrategy.getName() + "' is archived; reactivate it before subscribing");
        }

        Symbol symbol = Optional.ofNullable(symbolResolver.resolveOrNull(
                        request.getSymbolId(), request.getSymbol(), request.getExchangeCode()))
                .orElse(userStrategy.getSymbol());
        if (symbol == null) {
            throw new StrategyValidationException("This user strategy has no symbol; "
                    + "send symbolId, or symbol + exchangeCode, to subscribe it");
        }
        String timeframe = Optional.ofNullable(Timeframes.normalizeOrNull(request.getTimeframe()))
                .orElse(userStrategy.getTimeframe());
        if (timeframe == null) {
            throw new StrategyValidationException("This user strategy has no timeframe; "
                    + "send timeframe to subscribe it");
        }

        StrategySubscriptionRequest subscribe = new StrategySubscriptionRequest();
        subscribe.setStrategyId(userStrategy.getStrategy().getId());
        subscribe.setSymbolId(symbol.getId());
        subscribe.setTimeframe(timeframe);
        // The relational rows are the source of truth; this is the projection into
        // the flat map the execution path already consumes, which is what keeps the
        // config hash and the dedup working unchanged.
        subscribe.setParams(new LinkedHashMap<>(effectiveValuesByCode(userStrategy)));
        subscribe.setTradingAccountId(request.getTradingAccountId());
        subscribe.setRiskProfileId(request.getRiskProfileId());
        subscribe.setQuantity(request.getQuantity());
        subscribe.setMultiplier(request.getMultiplier());
        subscribe.setLotSize(request.getLotSize());
        subscribe.setCapitalAllocated(request.getCapitalAllocated());
        subscribe.setExecutionMode(request.getExecutionMode());
        subscribe.setTradeMode(request.getTradeMode());

        log.info("SUBSCRIBE from user strategy={} '{}' {} {} | user={}",
                id, userStrategy.getName(), symbol.getSymbol(), timeframe, email);

        // Delegated rather than reimplemented: dedup, the per-account uniqueness
        // rule and the instance lifecycle all live on the subscription path.
        return subscriptionService.create(email, subscribe);
    }

    // ------------------------------------------------------------ overrides

    private void applyOverrides(UserStrategy userStrategy, List<ParameterOverrideRequest> overrides) {
        if (overrides == null) {
            return;
        }
        for (ParameterOverrideRequest override : overrides) {
            if (override == null) {
                continue;
            }
            if (override.getParameterId() == null) {
                throw new StrategyValidationException("parameterId is required on every override");
            }
            Parameter parameter = parameterRepository.findById(override.getParameterId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Parameter", override.getParameterId()));

            boolean indicatorScoped =
                    override.getUserStrategyIndicatorId() != null || override.getIndicatorId() != null;
            if (indicatorScoped) {
                applyIndicatorOverride(userStrategy, override, parameter);
            } else {
                applyStrategyOverride(userStrategy, override, parameter);
            }
        }
    }

    private void applyIndicatorOverride(UserStrategy userStrategy, ParameterOverrideRequest override,
                                        Parameter parameter) {
        UserStrategyIndicator row = resolveIndicatorRow(userStrategy,
                override.getUserStrategyIndicatorId(), override.getIndicatorId(), override.getSlot());

        // The knob has to be one this indicator actually declares. Without this a
        // caller could pin 'sl' onto EMA and the value would resolve against
        // nothing, silently doing nothing at run time.
        IndicatorParameterLink link = indicatorParameterRepository
                .findByIndicator_IdAndParameter_Id(row.getIndicator().getId(), parameter.getId())
                .orElseThrow(() -> new StrategyValidationException("Parameter '" + parameter.getCode()
                        + "' does not belong to indicator '" + row.getIndicator().getName() + "'"));

        UserStrategyParameter existing = overrideRepository
                .findByUserStrategyIndicator_IdAndParameter_Id(row.getId(), parameter.getId())
                .orElse(null);

        if (override.getValue() == null) {
            clear(userStrategy, existing);
            return;
        }
        UserStrategyParameter stored = existing != null ? existing : new UserStrategyParameter();
        stored.setUserStrategy(userStrategy);
        stored.setUserStrategyIndicator(row);
        stored.setParameter(parameter);
        stored.setIndicatorParameterLink(link);
        stored.setCustomValue(override.getValue().trim());
        overrideRepository.save(stored);
        if (existing == null) {
            userStrategy.getParameters().add(stored);
        }
    }

    private void applyStrategyOverride(UserStrategy userStrategy, ParameterOverrideRequest override,
                                       Parameter parameter) {
        UUID templateId = userStrategy.getStrategy().getId();
        if (!strategyParameterRepository.existsByStrategy_IdAndParameter_Id(templateId, parameter.getId())) {
            throw new StrategyValidationException("Parameter '" + parameter.getCode()
                    + "' does not belong to strategy '" + userStrategy.getStrategy().getName()
                    + "'. Send indicatorId if it is an indicator knob.");
        }

        UserStrategyParameter existing = overrideRepository
                .findByUserStrategy_IdAndParameter_IdAndUserStrategyIndicatorIsNull(
                        userStrategy.getId(), parameter.getId())
                .orElse(null);

        if (override.getValue() == null) {
            clear(userStrategy, existing);
            return;
        }
        UserStrategyParameter stored = existing != null ? existing : new UserStrategyParameter();
        stored.setUserStrategy(userStrategy);
        stored.setUserStrategyIndicator(null);
        stored.setParameter(parameter);
        stored.setIndicatorParameterLink(null);
        stored.setCustomValue(override.getValue().trim());
        overrideRepository.save(stored);
        if (existing == null) {
            userStrategy.getParameters().add(stored);
        }
    }

    /** Clearing is a delete, not a null value: the knob has to fall back to the global default. */
    private void clear(UserStrategy userStrategy, UserStrategyParameter existing) {
        if (existing == null) {
            return;
        }
        userStrategy.getParameters().remove(existing);
        overrideRepository.delete(existing);
    }

    private UserStrategyIndicator resolveIndicatorRow(UserStrategy userStrategy, UUID rowId,
                                                      UUID indicatorId, String slot) {
        if (rowId != null) {
            return indicatorRowRepository.findByIdAndUserStrategy_Id(rowId, userStrategy.getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("User strategy indicator", rowId));
        }
        if (indicatorId == null) {
            throw new StrategyValidationException("userStrategyIndicatorId or indicatorId is required");
        }
        if (slot != null && !slot.isBlank()) {
            return indicatorRowRepository
                    .findByUserStrategy_IdAndIndicator_IdAndSlot(userStrategy.getId(), indicatorId, slot.trim())
                    .orElseThrow(() -> ResourceNotFoundException.of(
                            "Indicator slot '" + slot + "' on this user strategy", indicatorId));
        }
        List<UserStrategyIndicator> matches =
                indicatorRowRepository.findByUserStrategy_IdAndIndicator_Id(userStrategy.getId(), indicatorId);
        if (matches.isEmpty()) {
            throw ResourceNotFoundException.of("Indicator on this user strategy", indicatorId);
        }
        if (matches.size() > 1) {
            // Only reachable once a template uses one indicator more than once.
            throw new StrategyValidationException("Indicator " + indicatorId
                    + " is used more than once by this strategy; send slot to say which usage");
        }
        return matches.get(0);
    }

    // ------------------------------------------------------- effective values

    /**
     * Every knob in force, keyed by {@code parameters.code}.
     *
     * Indicator knobs first, then strategy knobs, first writer wins - the same
     * order and the same collapse-by-code rule
     * {@link StrategyParameterLinkSync#desiredDefs} uses to derive
     * {@code strategy_param_definitions}. That the two agree is what lets the
     * existing validator and config hash consume this map unchanged.
     */
    private Map<String, String> effectiveValuesByCode(UserStrategy userStrategy) {
        Map<String, String> values = new LinkedHashMap<>();
        for (UserStrategyIndicator row : indicatorRows(userStrategy)) {
            if (!row.isEnabled()) {
                continue;
            }
            for (EffectiveParameterResponse knob : indicatorKnobs(userStrategy, row)) {
                values.putIfAbsent(knob.code(), knob.effectiveValue());
            }
        }
        for (EffectiveParameterResponse knob : strategyKnobs(userStrategy)) {
            values.putIfAbsent(knob.code(), knob.effectiveValue());
        }
        return values;
    }

    /**
     * Runs the effective values through the engine's own validator.
     *
     * This is where a bad override is caught - type, range, and the cross-field
     * rules a single value cannot check on its own, like {@code d > k}. It also
     * returns the signal / execution split, so validation and the projection the
     * bot and the subscribe path need are one pass rather than two.
     */
    private ValidatedParams validateEffective(UserStrategy userStrategy) {
        Map<String, Object> submitted = new LinkedHashMap<>(effectiveValuesByCode(userStrategy));
        return paramValidator.validate(
                paramDefRepository.findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(
                        userStrategy.getStrategy().getId()),
                submitted);
    }

    private List<UserStrategyIndicator> indicatorRows(UserStrategy userStrategy) {
        return indicatorRowRepository.findByUserStrategy_IdOrderByDisplayOrderAsc(userStrategy.getId());
    }

    /** Indexed once per read so resolving N knobs is not N queries. */
    private Map<String, UserStrategyParameter> overridesByKey(UserStrategy userStrategy) {
        Map<String, UserStrategyParameter> byKey = new LinkedHashMap<>();
        for (UserStrategyParameter override : overrideRepository.findByUserStrategy_Id(userStrategy.getId())) {
            byKey.put(overrideKey(
                    override.isIndicatorScoped() ? override.getUserStrategyIndicator().getId() : null,
                    override.getParameter().getId()), override);
        }
        return byKey;
    }

    private String overrideKey(UUID indicatorRowId, Long parameterId) {
        return indicatorRowId + "#" + parameterId;
    }

    private List<EffectiveParameterResponse> indicatorKnobs(UserStrategy userStrategy,
                                                            UserStrategyIndicator row) {
        return indicatorKnobs(row, overridesByKey(userStrategy));
    }

    private List<EffectiveParameterResponse> indicatorKnobs(UserStrategyIndicator row,
                                                            Map<String, UserStrategyParameter> overrides) {
        List<EffectiveParameterResponse> knobs = new ArrayList<>();
        List<IndicatorParameterLink> links = indicatorParameterRepository
                .findByIndicator_IdOrderByDisplayOrderAscIdAsc(row.getIndicator().getId());
        for (IndicatorParameterLink link : links) {
            Parameter parameter = link.getParameter();
            UserStrategyParameter override = overrides.get(overrideKey(row.getId(), parameter.getId()));
            knobs.add(toKnob(parameter, link.effectiveDefault(), link.effectiveValidation(),
                    override, link.isRequired(), link.getDisplayOrder(), link.getId()));
        }
        return knobs;
    }

    private List<EffectiveParameterResponse> strategyKnobs(UserStrategy userStrategy) {
        return strategyKnobs(userStrategy, overridesByKey(userStrategy));
    }

    private List<EffectiveParameterResponse> strategyKnobs(UserStrategy userStrategy,
                                                           Map<String, UserStrategyParameter> overrides) {
        List<EffectiveParameterResponse> knobs = new ArrayList<>();
        List<StrategyParameterLink> links = strategyParameterRepository
                .findByStrategy_IdOrderByDisplayOrderAscIdAsc(userStrategy.getStrategy().getId());
        for (StrategyParameterLink link : links) {
            Parameter parameter = link.getParameter();
            UserStrategyParameter override = overrides.get(overrideKey(null, parameter.getId()));
            knobs.add(toKnob(parameter, link.effectiveDefault(), link.effectiveValidation(),
                    override, link.isRequired(), link.getDisplayOrder(), null));
        }
        return knobs;
    }

    /** The three-level fallback, in one place: custom, else link default, else catalog default. */
    private EffectiveParameterResponse toKnob(Parameter parameter, String linkDefault, String validation,
                                              UserStrategyParameter override, boolean required,
                                              int displayOrder, Long linkId) {
        String custom = override != null ? override.getCustomValue() : null;
        return new EffectiveParameterResponse(
                parameter.getId(),
                parameter.getCode(),
                parameter.getName(),
                parameter.getDataType(),
                parameter.getScope(),
                linkDefault,
                custom,
                custom != null ? custom : linkDefault,
                custom != null,
                json.toMap(validation),
                required,
                displayOrder,
                linkId);
    }

    // ------------------------------------------------------------ resolving

    private UserStrategy requireOwned(User user, UUID id) {
        return userStrategyRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("User strategy", id));
    }

    private StrategyTemplate resolveTemplate(UUID strategyId, String strategyName) {
        if (strategyId != null) {
            return templateRepository.findById(strategyId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Strategy template", strategyId));
        }
        if (strategyName != null && !strategyName.isBlank()) {
            String name = strategyName.trim();
            return templateRepository.findByName(name)
                    .orElseThrow(() -> ResourceNotFoundException.of("Strategy template", name));
        }
        throw new StrategyValidationException("strategyId or strategyName is required");
    }

    private void requireNameFree(User user, String name, UUID selfId) {
        userStrategyRepository.findByUser_IdAndNameIgnoreCase(user.getId(), name)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new ResourceConflictException("You already have a strategy named '"
                            + name + "' (id=" + other.getId() + ")");
                });
    }

    private String normalizeName(String submitted, String fallback) {
        String name = submitted == null || submitted.isBlank() ? fallback : submitted.trim();
        if (name.length() > NAME_MAX) {
            throw new StrategyValidationException("name must be at most " + NAME_MAX + " characters");
        }
        return name;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // -------------------------------------------------------------- mapping

    private UserStrategyResponse toResponse(UserStrategy userStrategy) {
        StrategyTemplate template = userStrategy.getStrategy();
        Symbol symbol = userStrategy.getSymbol();
        Map<String, UserStrategyParameter> overrides = overridesByKey(userStrategy);

        List<UserStrategyIndicatorResponse> indicators = new ArrayList<>();
        for (UserStrategyIndicator row : indicatorRows(userStrategy)) {
            Indicator indicator = row.getIndicator();
            indicators.add(new UserStrategyIndicatorResponse(
                    row.getId(),
                    indicator.getId(),
                    indicator.getName(),
                    row.getSlot(),
                    row.isEnabled(),
                    row.getDisplayOrder(),
                    indicatorKnobs(row, overrides)));
        }

        return new UserStrategyResponse(
                userStrategy.getId(),
                userStrategy.getUser().getId(),
                template.getId(),
                template.getName(),
                template.getDescription(),
                userStrategy.getName(),
                userStrategy.getDescription(),
                symbol != null ? symbol.getId() : null,
                symbol != null ? symbol.getSymbol() : null,
                userStrategy.getTimeframe(),
                indicators,
                strategyKnobs(userStrategy, overrides),
                overrides.size(),
                symbol != null && userStrategy.getTimeframe() != null,
                userStrategy.isActive(),
                userStrategy.getCreatedAt(),
                userStrategy.getUpdatedAt());
    }
}

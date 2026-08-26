package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.StrategyDeployRequest;
import com.example.tradeLedger.dto.StrategyDeploymentResponse;
import com.example.tradeLedger.dto.StrategySubscriptionRequest;
import com.example.tradeLedger.dto.StrategySubscriptionResponse;
import com.example.tradeLedger.dto.UserStrategyBulkDeleteResponse;
import com.example.tradeLedger.dto.UserStrategyGroupResponse;
import com.example.tradeLedger.dto.UserStrategyIndicatorResponse;
import com.example.tradeLedger.dto.UserStrategyRequest;
import com.example.tradeLedger.dto.UserStrategyResponse;
import com.example.tradeLedger.dto.UserStrategyRuntimeResponse;
import com.example.tradeLedger.entity.Derivative;
import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.LotRule;
import com.example.tradeLedger.entity.Moneyness;
import com.example.tradeLedger.entity.SharedStrategyConfig;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.entity.Symbol;
import com.example.tradeLedger.entity.TradingAccount;
import com.example.tradeLedger.entity.User;
import com.example.tradeLedger.entity.UserBroker;
import com.example.tradeLedger.entity.UserStrategy;
import com.example.tradeLedger.entity.UserStrategyIndicator;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.repository.IndicatorRepository;
import com.example.tradeLedger.repository.SharedStrategyConfigRepository;
import com.example.tradeLedger.repository.StrategySubscriptionRepository;
import com.example.tradeLedger.repository.StrategyTemplateRepository;
import com.example.tradeLedger.repository.TradingAccountRepository;
import com.example.tradeLedger.repository.UserBrokerRepository;
import com.example.tradeLedger.repository.UserStrategyIndicatorRepository;
import com.example.tradeLedger.repository.UserStrategyRepository;
import com.example.tradeLedger.service.CurrentUserService;
import com.example.tradeLedger.service.SharedStrategyConfigService;
import com.example.tradeLedger.service.UserStrategyService;
import com.example.tradeLedger.utils.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class UserStrategyServiceImpl implements UserStrategyService {

    private static final Logger log = LoggerFactory.getLogger(UserStrategyServiceImpl.class);

    private static final int NAME_MAX = 100;

    private final CurrentUserService currentUserService;
    private final UserStrategyRepository userStrategyRepository;
    private final UserStrategyIndicatorRepository indicatorRowRepository;
    private final StrategyTemplateRepository templateRepository;
    private final IndicatorRepository indicatorRepository;
    private final StrategySubscriptionRepository subscriptionRepository;
    private final SharedStrategyConfigRepository sharedConfigRepository;
    private final TradingAccountRepository tradingAccountRepository;
    private final UserBrokerRepository userBrokerRepository;
    private final SharedStrategyConfigService sharedConfigService;
    private final SubscriptionFanOut fanOut;
    private final IndicatorParams indicatorParams;
    private final UserStrategyValidator validator;
    private final SymbolResolver symbolResolver;
    private final RuleTrees ruleTrees;
    private final JsonSupport json;

    public UserStrategyServiceImpl(CurrentUserService currentUserService,
                                   UserStrategyRepository userStrategyRepository,
                                   UserStrategyIndicatorRepository indicatorRowRepository,
                                   StrategyTemplateRepository templateRepository,
                                   IndicatorRepository indicatorRepository,
                                   StrategySubscriptionRepository subscriptionRepository,
                                   SharedStrategyConfigRepository sharedConfigRepository,
                                   TradingAccountRepository tradingAccountRepository,
                                   UserBrokerRepository userBrokerRepository,
                                   SharedStrategyConfigService sharedConfigService,
                                   SubscriptionFanOut fanOut,
                                   IndicatorParams indicatorParams,
                                   UserStrategyValidator validator,
                                   SymbolResolver symbolResolver,
                                   RuleTrees ruleTrees,
                                   JsonSupport json) {
        this.currentUserService = currentUserService;
        this.userStrategyRepository = userStrategyRepository;
        this.indicatorRowRepository = indicatorRowRepository;
        this.templateRepository = templateRepository;
        this.indicatorRepository = indicatorRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.sharedConfigRepository = sharedConfigRepository;
        this.tradingAccountRepository = tradingAccountRepository;
        this.userBrokerRepository = userBrokerRepository;
        this.sharedConfigService = sharedConfigService;
        this.fanOut = fanOut;
        this.indicatorParams = indicatorParams;
        this.validator = validator;
        this.symbolResolver = symbolResolver;
        this.ruleTrees = ruleTrees;
        this.json = json;
    }

    // ----------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public List<UserStrategyResponse> list(String email, Boolean active, UUID strategyId) {
        List<UserStrategy> rows = ownedRows(email, active, strategyId);
        Batch batch = Batch.of(rows, indicatorRowRepository);
        return rows.stream().map(row -> toResponse(row, batch)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserStrategyGroupResponse> listGrouped(String email, Boolean active, UUID strategyId) {
        List<UserStrategy> rows = ownedRows(email, active, strategyId);
        Batch batch = Batch.of(rows, indicatorRowRepository);

        // Insertion-ordered so the rows inside each group keep the flat list's
        // oldest-first order; the GROUPS are then sorted by the name they are
        // tagged with, which is the order a list screen reads them in.
        Map<UUID, List<UserStrategy>> byTemplate = new LinkedHashMap<>();
        for (UserStrategy row : rows) {
            byTemplate.computeIfAbsent(row.getStrategy().getId(), key -> new ArrayList<>()).add(row);
        }

        // One query for every group's instance count rather than one per group -
        // it is a platform-wide fact about the template, so it is read from the
        // shared config table rather than from the caller's rows.
        Map<UUID, Long> instanceCounts = counts(
                sharedConfigRepository.countByStrategyIds(byTemplate.keySet()));

        List<UserStrategyGroupResponse> groups = new ArrayList<>(byTemplate.size());
        for (List<UserStrategy> group : byTemplate.values()) {
            // Every row in the group came from the same template, so any of them
            // can supply the tag.
            StrategyTemplate template = group.get(0).getStrategy();
            groups.add(new UserStrategyGroupResponse(
                    template.getId(),
                    template.getName(),
                    template.getDescription(),
                    template.isSystem(),
                    group.size(),
                    instanceCounts.getOrDefault(template.getId(), 0L),
                    group.stream().map(row -> toResponse(row, batch)).toList()));
        }
        groups.sort(Comparator.comparing(UserStrategyGroupResponse::strategyName,
                String.CASE_INSENSITIVE_ORDER));
        return groups;
    }

    /** {@code [id, count]} pairs to a map; a GROUP BY omits the zeroes. */
    private static Map<UUID, Long> counts(List<Object[]> pairs) {
        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (Object[] pair : pairs) {
            counts.put((UUID) pair[0], ((Number) pair[1]).longValue());
        }
        return counts;
    }

    /**
     * The one thing a list response needs that would otherwise be fetched per
     * row: each strategy's indicator usages.
     *
     * A response over N strategies asks the same question N times, and asking per
     * row is a database round trip per row; on a remote database that is the
     * whole latency of the endpoint. This asks once.
     *
     * Built from the rows the response is about, so it never holds more than the
     * request needs, and discarded with the response.
     */
    private record Batch(Map<UUID, List<UserStrategyIndicator>> indicatorRows) {

        static Batch of(List<UserStrategy> rows,
                        UserStrategyIndicatorRepository indicatorRowRepository) {
            List<UUID> ids = rows.stream().map(UserStrategy::getId).toList();
            if (ids.isEmpty()) {
                // No rows means no question to ask - and an empty IN () is a
                // query some databases refuse outright.
                return new Batch(Map.of());
            }

            Map<UUID, List<UserStrategyIndicator>> byStrategy = new LinkedHashMap<>();
            for (UserStrategyIndicator row : indicatorRowRepository
                    .findByUserStrategy_IdInOrderByUserStrategy_IdAscDisplayOrderAsc(ids)) {
                byStrategy.computeIfAbsent(row.getUserStrategy().getId(), key -> new ArrayList<>())
                        .add(row);
            }
            return new Batch(byStrategy);
        }

        List<UserStrategyIndicator> indicatorsOf(UserStrategy strategy) {
            return indicatorRows.getOrDefault(strategy.getId(), List.of());
        }
    }

    /**
     * The caller's strategies under both filters, oldest first - the one fetch the
     * flat and grouped shapes share, so they can never disagree about which rows
     * the caller has.
     */
    private List<UserStrategy> ownedRows(String email, Boolean active, UUID strategyId) {
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
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserStrategyResponse get(String email, UUID id) {
        return toResponse(requireOwned(currentUserService.require(email), id));
    }

    @Override
    @Transactional(readOnly = true)
    public UserStrategyRuntimeResponse runtime(String email, UUID id) {
        UserStrategy strategy = requireOwned(currentUserService.require(email), id);
        StrategyTemplate template = strategy.getStrategy();

        List<UserStrategyRuntimeResponse.Indicator> indicators = new ArrayList<>();
        for (UserStrategyIndicator row : indicatorRows(strategy)) {
            if (row.isEnabled()) {
                indicators.add(new UserStrategyRuntimeResponse.Indicator(
                        row.getIndicator().getId(), row.getIndicator().getName(), row.getSlot(),
                        json.toMap(row.getParams())));
            }
        }

        Symbol symbol = strategy.getSymbol();
        SharedStrategyConfig config = strategy.getSharedConfig();
        return new UserStrategyRuntimeResponse(
                strategy.getId(),
                strategy.getUser().getId(),
                template.getId(),
                template.getName(),
                template.getRuleTree(),
                symbol != null ? symbol.getId() : null,
                symbol != null ? symbol.getSymbol() : null,
                strategy.getCandleDuration(),
                strategy.getTriggerDuration(),
                strategy.isActive(),
                indicators,
                strategy.getDerivative().name(),
                validator.legs(strategy),
                strategy.getLotRule().name(),
                strategy.getBaseLot(),
                strategy.getAveragingCount(),
                strategy.getSlPct(),
                strategy.getTpPct(),
                signalParams(strategy),
                config != null ? config.getId() : null,
                config != null ? config.getConfigHash() : null);
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

        // Defaulting to the template name makes "save this as-is" a one-field
        // request; the second strategy from the same template has to be named.
        String name = normalizeName(request.getName(), template.getName());
        requireNameFree(user, name, null);

        UserStrategy strategy = new UserStrategy();
        strategy.setUser(user);
        strategy.setStrategy(template);
        strategy.setName(name);
        strategy.setActive(true);
        userStrategyRepository.save(strategy);

        seedIndicatorRows(strategy, template);
        applyRequest(strategy, request);

        log.info("CREATE strategy {} from template {} | user={}", name, template.getName(), email);
        return toResponse(strategy);
    }

    /**
     * Mirrors the template indicator set onto the strategy, one row per indicator
     * the rule tree names, each starting on its schema defaults.
     *
     * Read from the rule tree directly rather than from an index table beside it.
     * The tree is the only declaration of what a template uses, so deriving it
     * here is the only way the two can never disagree.
     */
    private void seedIndicatorRows(UserStrategy strategy, StrategyTemplate template) {
        int order = 0;
        for (String name : IndicatorResolver.indicatorNames(json.readTree(template.getRuleTree()))) {
            Indicator indicator = indicatorRepository.findByName(name).orElseThrow(() ->
                    new StrategyValidationException("Template " + template.getName()
                            + " references unknown indicator " + name
                            + " - the template cannot be used until its catalog entry exists"));

            UserStrategyIndicator row = new UserStrategyIndicator();
            row.setUserStrategy(strategy);
            row.setIndicator(indicator);
            row.setEnabled(true);
            row.setDisplayOrder(order++);
            row.setParams(json.toJson(indicatorParams.defaults(indicator)));
            indicatorRowRepository.save(row);
        }
    }

    // --------------------------------------------------------------- update

    @Override
    @Transactional
    public UserStrategyResponse update(String email, UUID id, UserStrategyRequest request) {
        User user = currentUserService.require(email);
        UserStrategy strategy = requireOwned(user, id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        if (request.getName() != null) {
            String name = normalizeName(request.getName(), strategy.getStrategy().getName());
            requireNameFree(user, name, strategy.getId());
            strategy.setName(name);
        }
        applyRequest(strategy, request);

        log.info("UPDATE strategy={} | user={}", id, email);
        return toResponse(strategy);
    }

    /**
     * Applies every present field, then validates and re-resolves once.
     *
     * One pass at the end rather than a check per setter: a leg cannot be judged
     * without knowing the derivative, and the shared config cannot be resolved
     * until every indicator value is settled.
     */
    private void applyRequest(UserStrategy strategy, UserStrategyRequest request) {
        if (request.getDescription() != null) {
            strategy.setDescription(trimToNull(request.getDescription()));
        }
        Symbol symbol = symbolResolver.resolveOrNull(
                request.getSymbolId(), request.getSymbol(), request.getExchangeCode());
        if (symbol != null) {
            strategy.setSymbol(symbol);
        }
        String candle = Timeframes.normalizeOrNull(request.getCandleDuration());
        if (candle != null) {
            strategy.setCandleDuration(candle);
        }
        String trigger = Timeframes.normalizeOrNull(request.getTriggerDuration());
        if (trigger != null) {
            strategy.setTriggerDuration(trigger);
        }

        Derivative derivative =
                UserStrategyValidator.parse(Derivative.class, request.getDerivative(), "derivative");
        if (derivative != null) {
            strategy.setDerivative(derivative);
        }
        applySide(strategy, request, true);
        applySide(strategy, request, false);

        LotRule lotRule = UserStrategyValidator.parse(LotRule.class, request.getLotRule(), "lotRule");
        if (lotRule != null) {
            strategy.setLotRule(lotRule);
        }
        if (request.getBaseLot() != null) {
            strategy.setBaseLot(request.getBaseLot());
        }
        if (request.getAveragingCount() != null) {
            strategy.setAveragingCount(request.getAveragingCount());
        }
        if (request.getSlPct() != null) {
            strategy.setSlPct(request.getSlPct());
        }
        if (request.getTpPct() != null) {
            strategy.setTpPct(request.getTpPct());
        }
        if (request.getActive() != null) {
            strategy.setActive(request.getActive());
        }

        applyIndicatorTuning(strategy, request.getIndicators());

        validator.validate(strategy);
        resolveSharedConfig(strategy);
        userStrategyRepository.save(strategy);
    }

    /** The two sides differ only in which triple of columns they touch. */
    private void applySide(UserStrategy strategy, UserStrategyRequest request, boolean call) {
        Boolean enabled = call ? request.getCeEnabled() : request.getPeEnabled();
        String moneynessText = call ? request.getCeMoneyness() : request.getPeMoneyness();
        Integer offset = call ? request.getCeStrikeOffset() : request.getPeStrikeOffset();
        String field = call ? "ceMoneyness" : "peMoneyness";

        Moneyness moneyness = UserStrategyValidator.parse(Moneyness.class, moneynessText, field);

        if (call) {
            if (enabled != null) strategy.setCeEnabled(enabled);
            if (moneyness != null) strategy.setCeMoneyness(moneyness);
            if (offset != null) strategy.setCeStrikeOffset(offset);
            // Naming a moneyness is how a side is turned on: nobody sends
            // ceMoneyness=OTM meaning "but leave the call side off".
            if (moneyness != null && enabled == null) strategy.setCeEnabled(true);
        } else {
            if (enabled != null) strategy.setPeEnabled(enabled);
            if (moneyness != null) strategy.setPeMoneyness(moneyness);
            if (offset != null) strategy.setPeStrikeOffset(offset);
            if (moneyness != null && enabled == null) strategy.setPeEnabled(true);
        }
    }

    // ------------------------------------------------------------ indicators

    private void applyIndicatorTuning(UserStrategy strategy,
                                      List<UserStrategyRequest.IndicatorTuning> tunings) {
        if (tunings == null) {
            return;
        }
        for (UserStrategyRequest.IndicatorTuning tuning : tunings) {
            if (tuning == null) {
                continue;
            }
            UserStrategyIndicator row = resolveIndicatorRow(strategy, tuning);
            if (tuning.getEnabled() != null) {
                row.setEnabled(tuning.getEnabled());
            }
            if (tuning.getParams() != null) {
                // Merged over what is stored, so sending one key changes one key.
                Map<String, Object> merged = new LinkedHashMap<>(json.toMap(row.getParams()));
                merged.putAll(tuning.getParams());
                row.setParams(json.toJson(indicatorParams.effective(row.getIndicator(), merged)));
            }
            indicatorRowRepository.save(row);
        }
    }

    private UserStrategyIndicator resolveIndicatorRow(UserStrategy strategy,
                                                      UserStrategyRequest.IndicatorTuning tuning) {
        if (tuning.getUserStrategyIndicatorId() != null) {
            return indicatorRowRepository
                    .findByIdAndUserStrategy_Id(tuning.getUserStrategyIndicatorId(), strategy.getId())
                    .orElseThrow(() -> ResourceNotFoundException.of(
                            "Indicator on this strategy", tuning.getUserStrategyIndicatorId()));
        }

        UUID indicatorId = tuning.getIndicatorId();
        if (indicatorId == null && tuning.getIndicatorName() != null) {
            String name = tuning.getIndicatorName().trim().toUpperCase(java.util.Locale.ROOT);
            indicatorId = indicatorRepository.findByName(name)
                    .orElseThrow(() -> ResourceNotFoundException.of("Indicator", name))
                    .getId();
        }
        if (indicatorId == null) {
            throw new StrategyValidationException(
                    "userStrategyIndicatorId, indicatorId or indicatorName is required on every indicator entry");
        }

        String slot = tuning.getSlot() == null || tuning.getSlot().isBlank() ? null : tuning.getSlot().trim();
        if (slot != null) {
            UUID id = indicatorId;
            return indicatorRowRepository
                    .findByUserStrategy_IdAndIndicator_IdAndSlot(strategy.getId(), indicatorId, slot)
                    .orElseThrow(() -> ResourceNotFoundException.of(
                            "Indicator slot " + slot + " on this strategy", id));
        }
        List<UserStrategyIndicator> matches =
                indicatorRowRepository.findByUserStrategy_IdAndIndicator_Id(strategy.getId(), indicatorId);
        if (matches.isEmpty()) {
            throw ResourceNotFoundException.of("Indicator on this strategy", indicatorId);
        }
        if (matches.size() > 1) {
            // Only reachable once a template uses one indicator more than once.
            throw new StrategyValidationException("Indicator " + indicatorId
                    + " is used more than once by this strategy; send slot to say which usage");
        }
        return matches.get(0);
    }

    /**
     * Every enabled indicator values, unioned and sorted - the entire input to the
     * config hash.
     *
     * A key reachable through two indicators appears once: the rule tree binds
     * {@code $k} to one value, so two indicators declaring {@code k} are asking for
     * the same number, and hashing it twice would be the same hash anyway.
     */
    private Map<String, Object> signalParams(UserStrategy strategy) {
        Map<String, Object> params = new TreeMap<>();
        for (UserStrategyIndicator row : indicatorRows(strategy)) {
            if (row.isEnabled()) {
                json.toMap(row.getParams()).forEach(params::putIfAbsent);
            }
        }
        return params;
    }

    // ------------------------------------------------------------ dedup unit

    /**
     * Points the strategy at the shared computation its current configuration
     * comes to, creating it only if nobody already runs that exact math.
     *
     * The previous one is retired when its last active deployment leaves, so
     * lineage survives a retune and an abandoned computation stops being scheduled.
     */
    private void resolveSharedConfig(UserStrategy strategy) {
        if (strategy.getSymbol() == null || strategy.getCandleDuration() == null) {
            return;
        }
        Map<String, Object> signal = signalParams(strategy);
        ruleTrees.assertResolves(strategy.getStrategy(), signal);

        SharedStrategyConfig previous = strategy.getSharedConfig();
        SharedStrategyConfigService.Resolution resolution = sharedConfigService.resolveOrCreate(
                strategy.getStrategy(), strategy.getSymbol(), strategy.getCandleDuration(), signal);
        SharedStrategyConfig target = resolution.instance();

        if (previous != null && previous.getId().equals(target.getId())) {
            return;
        }
        // Lineage: a computation created by this retune records what it replaced,
        // so a retired one can still be traced back from the row that took over.
        if (previous != null && resolution.created() && target.getSupersedes() == null) {
            target.setSupersedes(previous);
        }
        strategy.setSharedConfig(target);
        userStrategyRepository.save(strategy);
        if (previous != null) {
            sharedConfigService.retireIfOrphaned(previous.getId());
            log.info("REPOINT strategy={} instance {} -> {} ({})", strategy.getId(),
                    previous.getId(), target.getId(), resolution.created() ? "new" : "shared");
        }
    }

    // --------------------------------------------------------------- delete

    @Override
    @Transactional
    public void delete(String email, UUID id) {
        User user = currentUserService.require(email);
        UserStrategy strategy = requireOwned(user, id);

        // Deployments hold a FK to this row, so a live one has to be withdrawn
        // first - a cascade here would silently stop trading on every broker.
        long deployments = subscriptionRepository.countByUserStrategy_Id(id);
        if (deployments > 0) {
            throw new ResourceConflictException(stillDeployed(strategy, deployments));
        }

        SharedStrategyConfig config = strategy.getSharedConfig();
        userStrategyRepository.delete(strategy);
        userStrategyRepository.flush();
        if (config != null) {
            sharedConfigService.retireIfOrphaned(config.getId());
        }
        log.info("DELETE strategy={} {} | user={}", id, strategy.getName(), email);
    }

    @Override
    @Transactional
    public UserStrategyBulkDeleteResponse deleteAll(String email, Boolean active, UUID strategyId) {
        List<UserStrategy> rows = ownedRows(email, active, strategyId);
        if (rows.isEmpty()) {
            log.info("DELETE-ALL matched nothing active={} template={} | user={}", active, strategyId, email);
            return new UserStrategyBulkDeleteResponse(0, 0, 0, List.of());
        }

        // One question for the whole sweep rather than one per row - the same
        // batched count the list responses use.
        Map<UUID, Long> deploymentCounts = counts(subscriptionRepository.countByUserStrategyIds(
                rows.stream().map(UserStrategy::getId).toList()));

        // Every deleted row's computation, deduplicated: several of these rows can
        // share one, and it is only retired once, after the deletes have hit the
        // database - otherwise it still looks occupied by rows about to vanish.
        Set<UUID> touchedConfigs = new LinkedHashSet<>();
        List<UserStrategyBulkDeleteResponse.Item> results = new ArrayList<>(rows.size());
        int deleted = 0;
        int skipped = 0;

        for (UserStrategy strategy : rows) {
            StrategyTemplate template = strategy.getStrategy();
            long deployments = deploymentCounts.getOrDefault(strategy.getId(), 0L);

            // Deployments hold a FK to this row. One that is still live is left
            // standing rather than failing the sweep, so clearing ten strategies
            // is not blocked by the one broker still running an eleventh.
            if (deployments > 0) {
                results.add(new UserStrategyBulkDeleteResponse.Item(
                        strategy.getId(), strategy.getName(), template.getId(), template.getName(),
                        UserStrategyBulkDeleteResponse.STATUS_SKIPPED, deployments,
                        stillDeployed(strategy, deployments)));
                skipped++;
                continue;
            }

            if (strategy.getSharedConfig() != null) {
                touchedConfigs.add(strategy.getSharedConfig().getId());
            }
            results.add(new UserStrategyBulkDeleteResponse.Item(
                    strategy.getId(), strategy.getName(), template.getId(), template.getName(),
                    UserStrategyBulkDeleteResponse.STATUS_DELETED, 0L, null));
            userStrategyRepository.delete(strategy);
            deleted++;
        }

        userStrategyRepository.flush();
        for (UUID configId : touchedConfigs) {
            sharedConfigService.retireIfOrphaned(configId);
        }
        log.info("DELETE-ALL strategies deleted={} skipped={} active={} template={} | user={}",
                deleted, skipped, active, strategyId, email);
        return new UserStrategyBulkDeleteResponse(rows.size(), deleted, skipped, results);
    }

    /** The one sentence both delete paths tell a caller about a live deployment. */
    private static String stillDeployed(UserStrategy strategy, long deployments) {
        return "Strategy " + strategy.getName() + " is deployed on " + deployments
                + " account(s). Withdraw those deployments first, or archive it with "
                + "PUT /api/v1/my-strategies/" + strategy.getId() + " {\"active\":false}.";
    }

    // --------------------------------------------------------------- deploy

    @Override
    @Transactional(readOnly = true)
    public StrategyDeploymentResponse deploy(String email, UUID id, StrategyDeployRequest request) {
        User user = currentUserService.require(email);
        UserStrategy strategy = requireOwned(user, id);
        if (request == null || request.getTargets() == null || request.getTargets().isEmpty()) {
            throw new StrategyValidationException("targets is required and must name at least one account");
        }
        if (!strategy.isActive()) {
            throw new StrategyValidationException("Strategy " + strategy.getName()
                    + " is archived; reactivate it before deploying");
        }
        if (!strategy.isDeployable()) {
            throw new StrategyValidationException("Strategy " + strategy.getName()
                    + " has no market yet - set symbol and candleDuration before deploying");
        }

        List<Destination> destinations = expand(user, request);
        List<StrategyDeploymentResponse.Item> results = new ArrayList<>(destinations.size());
        int deployed = 0;

        for (Destination destination : destinations) {
            try {
                // Its own transaction: a rejected account must not roll back the
                // accounts that already succeeded.
                StrategySubscriptionResponse subscription =
                        fanOut.deployOne(email, toSubscriptionRequest(id, destination));
                deployed++;
                results.add(item(destination, StrategyDeploymentResponse.STATUS_DEPLOYED, subscription, null));
            } catch (RuntimeException e) {
                log.warn("DEPLOY strategy={} account={} failed: {}",
                        id, destination.account().getId(), e.getMessage());
                results.add(item(destination, StrategyDeploymentResponse.STATUS_FAILED, null, e.getMessage()));
            }
        }

        Symbol symbol = strategy.getSymbol();
        SharedStrategyConfig config = strategy.getSharedConfig();
        log.info("DEPLOY strategy={} {} to {} account(s): {} deployed, {} failed | user={}",
                id, strategy.getName(), destinations.size(), deployed,
                destinations.size() - deployed, email);

        return new StrategyDeploymentResponse(
                strategy.getId(),
                strategy.getName(),
                symbol.getId(),
                symbol.getSymbol(),
                strategy.getCandleDuration(),
                config.getId(),
                config.getConfigHash(),
                destinations.size(),
                deployed,
                destinations.size() - deployed,
                results);
    }

    /** One account and the settings resolved for it: target first, then request. */
    private record Destination(TradingAccount account, UUID riskProfileId,
                               java.math.BigDecimal multiplier, java.math.BigDecimal capitalAllocated,
                               String executionMode, String tradeMode) {
    }

    /**
     * Turns targets into accounts, resolving a broker setup into every account
     * under it and rejecting an account named twice before anything is written.
     */
    private List<Destination> expand(User user, StrategyDeployRequest request) {
        List<Destination> destinations = new ArrayList<>();
        Map<UUID, String> seen = new LinkedHashMap<>();

        for (StrategyDeployRequest.Target target : request.getTargets()) {
            if (target == null) {
                continue;
            }
            for (TradingAccount account : accountsFor(user, target)) {
                String previous = seen.put(account.getId(), account.getAccountName());
                if (previous != null) {
                    throw new StrategyValidationException("Account " + previous
                            + " is named twice in targets - a strategy is deployed on an account once");
                }
                destinations.add(new Destination(
                        account,
                        first(target.getRiskProfileId(), request.getRiskProfileId()),
                        first(target.getMultiplier(), request.getMultiplier()),
                        first(target.getCapitalAllocated(), request.getCapitalAllocated()),
                        first(target.getExecutionMode(), request.getExecutionMode()),
                        first(target.getTradeMode(), request.getTradeMode())));
            }
        }
        if (destinations.isEmpty()) {
            throw new StrategyValidationException("targets named no usable trading account");
        }
        return destinations;
    }

    private List<TradingAccount> accountsFor(User user, StrategyDeployRequest.Target target) {
        if (target.getTradingAccountId() != null) {
            return List.of(tradingAccountRepository
                    .findByIdAndUser_Id(target.getTradingAccountId(), user.getId())
                    .orElseThrow(() -> ResourceNotFoundException.of(
                            "Trading account", target.getTradingAccountId())));
        }
        if (target.getUserBrokerId() != null) {
            UserBroker broker = userBrokerRepository
                    .findByIdAndUser_Id(target.getUserBrokerId(), user.getId())
                    .orElseThrow(() -> ResourceNotFoundException.of(
                            "Broker setup", target.getUserBrokerId()));
            List<TradingAccount> accounts =
                    tradingAccountRepository.findByUserBroker_IdOrderByAccountNameAsc(broker.getId());
            if (accounts.isEmpty()) {
                throw new StrategyValidationException("Broker setup " + broker.getLabel()
                        + " has no trading accounts to deploy on");
            }
            return accounts;
        }
        throw new StrategyValidationException(
                "Every target needs a tradingAccountId or a userBrokerId");
    }

    private StrategySubscriptionRequest toSubscriptionRequest(UUID userStrategyId, Destination destination) {
        StrategySubscriptionRequest request = new StrategySubscriptionRequest();
        request.setUserStrategyId(userStrategyId);
        request.setTradingAccountId(destination.account().getId());
        request.setRiskProfileId(destination.riskProfileId());
        request.setMultiplier(destination.multiplier());
        request.setCapitalAllocated(destination.capitalAllocated());
        request.setExecutionMode(destination.executionMode());
        request.setTradeMode(destination.tradeMode());
        return request;
    }

    private StrategyDeploymentResponse.Item item(Destination destination, String status,
                                                 StrategySubscriptionResponse subscription, String error) {
        UserBroker broker = destination.account().getUserBroker();
        return new StrategyDeploymentResponse.Item(
                destination.account().getId(),
                destination.account().getAccountName(),
                broker.getId(),
                broker.getLabel(),
                status,
                subscription,
                error);
    }

    private static <T> T first(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }

    // ------------------------------------------------------------ resolving

    private List<UserStrategyIndicator> indicatorRows(UserStrategy strategy) {
        return indicatorRowRepository.findByUserStrategy_IdOrderByDisplayOrderAsc(strategy.getId());
    }

    private UserStrategy requireOwned(User user, UUID id) {
        return userStrategyRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Strategy", id));
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
                    throw new ResourceConflictException("You already have a strategy named "
                            + name + " (id=" + other.getId() + ")");
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

    /** The single-strategy path: a batch of one, so there is one mapper, not two. */
    private UserStrategyResponse toResponse(UserStrategy strategy) {
        return toResponse(strategy, Batch.of(List.of(strategy), indicatorRowRepository));
    }

    private UserStrategyResponse toResponse(UserStrategy strategy, Batch batch) {
        StrategyTemplate template = strategy.getStrategy();
        Symbol symbol = strategy.getSymbol();

        List<UserStrategyIndicatorResponse> indicators = new ArrayList<>();
        for (UserStrategyIndicator row : batch.indicatorsOf(strategy)) {
            Indicator indicator = row.getIndicator();
            UserStrategyIndicatorResponse usage = new UserStrategyIndicatorResponse(
                    row.getId(),
                    indicator.getId(),
                    indicator.getName(),
                    row.getSlot(),
                    row.isEnabled(),
                    row.getDisplayOrder(),
                    json.toMap(row.getParams()),
                    json.toMap(indicator.getParamSchema()));
            indicators.add(usage);
        }

        return new UserStrategyResponse(
                strategy.getId(),
                template.getId(),
                template.getName(),
                strategy.getName(),
                strategy.getDescription(),
                symbol != null ? symbol.getSymbol() : null,
                symbol != null ? symbol.getExchange().getCode() : null,
                strategy.getCandleDuration(),
                strategy.getTriggerDuration(),
                strategy.getDerivative().name(),
                strategy.isCeEnabled(),
                strategy.getCeMoneyness() != null ? strategy.getCeMoneyness().name() : null,
                strategy.getCeStrikeOffset(),
                strategy.isPeEnabled(),
                strategy.getPeMoneyness() != null ? strategy.getPeMoneyness().name() : null,
                strategy.getPeStrikeOffset(),
                strategy.getLotRule().name(),
                strategy.getBaseLot(),
                strategy.getAveragingCount(),
                strategy.getSlPct(),
                strategy.getTpPct(),
                indicators,
                strategy.isDeployable(),
                strategy.isActive(),
                strategy.getCreatedAt(),
                strategy.getUpdatedAt());
    }
}

package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.IndicatorPlanResponse;
import com.example.tradeLedger.dto.StrategyInstanceResponse;
import com.example.tradeLedger.entity.Strategy;
import com.example.tradeLedger.entity.StrategyInstance;
import com.example.tradeLedger.entity.Symbol;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.repository.StrategyInstanceRepository;
import com.example.tradeLedger.repository.SubscriptionRepository;
import com.example.tradeLedger.service.StrategyInstanceService;
import com.example.tradeLedger.utils.CanonicalJson;
import com.example.tradeLedger.utils.ConfigHashUtil;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class StrategyInstanceServiceImpl implements StrategyInstanceService {

    private static final Logger log = LoggerFactory.getLogger(StrategyInstanceServiceImpl.class);

    private final StrategyInstanceRepository instanceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final JsonSupport json;

    public StrategyInstanceServiceImpl(StrategyInstanceRepository instanceRepository,
                                       SubscriptionRepository subscriptionRepository,
                                       JsonSupport json) {
        this.instanceRepository = instanceRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.json = json;
    }

    // ---------------------------------------------------------------- dedup

    @Override
    @Transactional
    public Resolution resolveOrCreate(Strategy strategy, Symbol symbol, String timeframe,
                                      Map<String, Object> signalParams) {
        JsonNode paramNode = json.toNode(signalParams);
        String canonical = CanonicalJson.canonicalize(paramNode);
        String configHash = ConfigHashUtil.configHash(strategy.getId(), symbol.getId(), timeframe, paramNode);

        StrategyInstance existing = instanceRepository
                .findByStrategy_IdAndSymbol_IdAndTimeframeAndConfigHash(
                        strategy.getId(), symbol.getId(), timeframe, configHash)
                .orElse(null);
        if (existing != null) {
            reviveIfRetired(existing);
            return new Resolution(existing, false);
        }

        StrategyInstance instance = new StrategyInstance();
        instance.setStrategy(strategy);
        instance.setSymbol(symbol);
        instance.setTimeframe(timeframe);
        instance.setSignalParams(canonical);
        // Recomputed server-side by trg_instances_hash; set here so the row is
        // consistent even on a schema created by ddl-auto alone.
        instance.setConfigHash(configHash);
        instance.setStatus(StrategyInstance.STATUS_ACTIVE);

        // UNIQUE (strategy_id, symbol_id, timeframe, config_hash) is the real
        // guarantee; the lookup above only avoids the insert in the common case.
        // A lost race therefore surfaces as a constraint violation, which is
        // deliberately NOT caught here: a failed flush leaves the surrounding
        // transaction marked rollback-only, so "recovering" by re-reading the
        // winner would only produce a confusing failure at commit. It propagates
        // instead and the caller gets a 409 on a request that succeeds on retry.
        StrategyInstance saved = instanceRepository.saveAndFlush(instance);
        log.info("NEW instance {} strategy='{}' {} {} params={} hash={}",
                saved.getId(), strategy.getName(), symbol.getSymbol(), timeframe,
                CanonicalJson.describe(signalParams), configHash);
        return new Resolution(saved, true);
    }

    @Override
    @Transactional
    public void retireIfOrphaned(UUID instanceId) {
        if (subscriptionRepository.countByStrategyInstance_IdAndActiveTrue(instanceId) > 0) {
            return;
        }
        instanceRepository.findById(instanceId).ifPresent(instance -> {
            if (!StrategyInstance.STATUS_RETIRED.equals(instance.getStatus())) {
                instance.setStatus(StrategyInstance.STATUS_RETIRED);
                instanceRepository.save(instance);
                log.info("RETIRE instance {} hash={} (no active subscribers)",
                        instanceId, instance.getConfigHash());
            }
        });
    }

    @Override
    @Transactional
    public void reviveIfRetired(StrategyInstance instance) {
        if (StrategyInstance.STATUS_RETIRED.equals(instance.getStatus())) {
            instance.setStatus(StrategyInstance.STATUS_ACTIVE);
            instanceRepository.save(instance);
            log.info("REVIVE instance {} hash={}", instance.getId(), instance.getConfigHash());
        }
    }

    // --------------------------------------------------------------- reads

    @Override
    @Transactional(readOnly = true)
    public List<StrategyInstanceResponse> list(String status) {
        List<StrategyInstance> instances = status == null || status.isBlank()
                ? instanceRepository.findAll()
                : instanceRepository.findByStatusOrderByCreatedAtDesc(status.trim().toLowerCase());
        return instances.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StrategyInstanceResponse get(UUID id) {
        return toResponse(instanceRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Strategy instance", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public IndicatorPlanResponse indicatorPlan() {
        List<StrategyInstance> active =
                instanceRepository.findByStatusOrderByCreatedAtDesc(StrategyInstance.STATUS_ACTIVE);

        long subscriptions = 0;
        Set<String> fingerprints = new TreeSet<>();
        for (StrategyInstance instance : active) {
            subscriptions += subscriptionRepository.countByStrategyInstance_IdAndActiveTrue(instance.getId());
            fingerprints.addAll(resolveIndicators(instance));
        }
        return IndicatorPlanResponse.of(subscriptions, active.size(), new ArrayList<>(fingerprints));
    }

    // -------------------------------------------------------------- mapping

    private StrategyInstanceResponse toResponse(StrategyInstance instance) {
        Strategy strategy = instance.getStrategy();
        Symbol symbol = instance.getSymbol();
        return new StrategyInstanceResponse(
                instance.getId(),
                strategy.getId(),
                strategy.getName(),
                symbol.getId(),
                symbol.getSymbol(),
                instance.getTimeframe(),
                json.toMap(instance.getSignalParams()),
                instance.getConfigHash(),
                instance.getSupersedes() != null ? instance.getSupersedes().getId() : null,
                instance.getStatus(),
                new ArrayList<>(resolveIndicators(instance)),
                subscriptionRepository.countByStrategyInstance_IdAndActiveTrue(instance.getId()),
                instance.getCreatedAt());
    }

    /**
     * The concrete computations this instance needs. A rule tree that binds a key
     * the params do not supply is a data problem, not a request problem, so it is
     * logged and reported as an empty set rather than failing the read.
     */
    private Set<String> resolveIndicators(StrategyInstance instance) {
        JsonNode ruleTree = json.readTree(instance.getStrategy().getRuleTree());
        JsonNode params = json.readTree(instance.getSignalParams());
        if (ruleTree == null) {
            return Set.of();
        }
        try {
            return IndicatorResolver.resolve(ruleTree, params);
        } catch (RuntimeException e) {
            log.warn("Could not resolve indicators for instance {}: {}", instance.getId(), e.getMessage());
            return Set.of();
        }
    }
}

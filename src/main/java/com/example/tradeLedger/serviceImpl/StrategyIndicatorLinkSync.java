package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.entity.StrategyIndicatorLink;
import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.repository.IndicatorRepository;
import com.example.tradeLedger.repository.StrategyIndicatorLinkRepository;
import com.example.tradeLedger.repository.StrategyTemplateRepository;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps {@code strategy_indicator_links} in step with each strategy's rule tree.
 *
 * The rule tree stays the source of truth; these rows are its index. Every
 * strategy write calls {@link #sync}, and {@link #syncAll} rebuilds the lot at
 * startup so rows seeded or inserted outside the API are covered too.
 *
 * Rows are reconciled key by key rather than deleted and re-inserted, so a
 * strategy that keeps using EMA keeps the same mapping row - and no delete/insert
 * pair can trip the unique constraint inside one transaction.
 */
@Component
public class StrategyIndicatorLinkSync {

    private static final Logger log = LoggerFactory.getLogger(StrategyIndicatorLinkSync.class);

    private final StrategyTemplateRepository strategyRepository;
    private final StrategyIndicatorLinkRepository strategyIndicatorRepository;
    private final IndicatorRepository indicatorRepository;
    private final JsonSupport json;

    public StrategyIndicatorLinkSync(StrategyTemplateRepository strategyRepository,
                                         StrategyIndicatorLinkRepository strategyIndicatorRepository,
                                         IndicatorRepository indicatorRepository,
                                         JsonSupport json) {
        this.strategyRepository = strategyRepository;
        this.strategyIndicatorRepository = strategyIndicatorRepository;
        this.indicatorRepository = indicatorRepository;
        this.json = json;
    }

    /**
     * Rewrites this strategy's mapping rows to match its rule tree.
     *
     * A name the tree references but the catalog does not have cannot be mapped -
     * there is no row to point a foreign key at. That is already reported as
     * {@code unknownIndicators} on the strategy response, so it is skipped here
     * rather than failing the save.
     *
     * @return the mapped indicators, ordered by name
     */
    @Transactional
    public List<Indicator> sync(StrategyTemplate strategy) {
        JsonNode tree = json.readTree(strategy.getRuleTree());
        Set<String> names = tree == null ? Set.of() : IndicatorResolver.indicatorNames(tree);

        Map<String, Indicator> resolved = new LinkedHashMap<>();
        for (String name : names) {
            indicatorRepository.findByName(name).ifPresent(def -> resolved.put(name, def));
        }

        List<StrategyIndicatorLink> existing =
                strategyIndicatorRepository.findByStrategy_IdOrderByIndicator_NameAsc(strategy.getId());

        existing.stream()
                .filter(row -> !resolved.containsKey(row.getIndicator().getName()))
                .forEach(strategyIndicatorRepository::delete);

        Set<String> alreadyMapped = new HashSet<>();
        existing.forEach(row -> alreadyMapped.add(row.getIndicator().getName()));

        for (Map.Entry<String, Indicator> entry : resolved.entrySet()) {
            if (alreadyMapped.contains(entry.getKey())) {
                continue;
            }
            StrategyIndicatorLink row = new StrategyIndicatorLink();
            row.setStrategy(strategy);
            row.setIndicator(entry.getValue());
            strategyIndicatorRepository.save(row);
        }

        return new ArrayList<>(resolved.values());
    }

    /** Drops every mapping row for a strategy that is being deleted. */
    @Transactional
    public void clear(UUID strategyId) {
        strategyIndicatorRepository.deleteByStrategy_Id(strategyId);
    }

    /**
     * The indicators currently mapped to a strategy, as name to id.
     *
     * Returned as a map rather than a bare id list so a caller can emit ids in
     * the same order it emits names, whatever order that is.
     */
    @Transactional(readOnly = true)
    public Map<String, UUID> indicatorIdsByName(UUID strategyId) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        strategyIndicatorRepository.findByStrategy_IdOrderByIndicator_NameAsc(strategyId)
                .forEach(row -> ids.put(row.getIndicator().getName(), row.getIndicator().getId()));
        return ids;
    }

    /** Names of the strategies mapped to an indicator - the reverse lookup. */
    @Transactional(readOnly = true)
    public List<String> strategyNames(UUID indicatorId) {
        return strategyIndicatorRepository.findByIndicator_IdOrderByStrategy_NameAsc(indicatorId).stream()
                .map(row -> row.getStrategy().getName())
                .toList();
    }

    /**
     * Rebuilds the index for every strategy. Called once at startup so seeded
     * rows - and any strategy inserted straight into the database - are mapped
     * without waiting for someone to edit them through the API.
     */
    @Transactional
    public void syncAll() {
        int mapped = 0;
        for (StrategyTemplate strategy : strategyRepository.findAllByOrderByNameAsc()) {
            mapped += sync(strategy).size();
        }
        log.info("StrategyTemplate-indicator index rebuilt: {} mapping(s)", mapped);
    }
}

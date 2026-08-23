package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.IndicatorRequest;
import com.example.tradeLedger.dto.IndicatorResponse;
import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.repository.IndicatorRepository;
import com.example.tradeLedger.repository.StrategyTemplateRepository;
import com.example.tradeLedger.repository.UserStrategyIndicatorRepository;
import com.example.tradeLedger.service.IndicatorCatalogService;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class IndicatorCatalogServiceImpl implements IndicatorCatalogService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorCatalogServiceImpl.class);

    private static final int NAME_MAX = 50;

    private final IndicatorRepository indicatorRepository;
    private final StrategyTemplateRepository strategyRepository;
    private final UserStrategyIndicatorRepository userStrategyIndicatorRepository;
    private final StrategyTemplateValidator validator;
    private final JsonSupport json;

    public IndicatorCatalogServiceImpl(IndicatorRepository indicatorRepository,
                                          StrategyTemplateRepository strategyRepository,
                                          UserStrategyIndicatorRepository userStrategyIndicatorRepository,
                                          StrategyTemplateValidator validator,
                                          JsonSupport json) {
        this.indicatorRepository = indicatorRepository;
        this.strategyRepository = strategyRepository;
        this.userStrategyIndicatorRepository = userStrategyIndicatorRepository;
        this.validator = validator;
        this.json = json;
    }

    // -------------------------------------------------------------- queries

    @Override
    @Transactional(readOnly = true)
    public List<IndicatorResponse> list(Boolean active) {
        List<Indicator> defs = active == null
                ? indicatorRepository.findAllByOrderByNameAsc()
                : indicatorRepository.findByActiveOrderByNameAsc(active);
        return defs.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IndicatorResponse get(UUID id) {
        return toResponse(requireIndicator(id));
    }

    @Override
    @Transactional(readOnly = true)
    public IndicatorResponse getByName(String name) {
        Indicator def = indicatorRepository.findByName(validator.normalizeIndicatorName(name))
                .orElseThrow(() -> ResourceNotFoundException.of("Indicator", name));
        return toResponse(def);
    }

    // --------------------------------------------------------- create/update

    @Override
    @Transactional
    public IndicatorResponse create(IndicatorRequest request) {
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        List<String> errors = new ArrayList<>();
        String name = validator.normalizeIndicatorName(request.getName());
        if (name == null || name.isBlank()) {
            errors.add("name is required");
        } else if (name.length() > NAME_MAX) {
            errors.add("name must be at most " + NAME_MAX + " characters");
        }
        errors.addAll(validator.validateParamSchema(request.getParamSchema()));
        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }
        if (indicatorRepository.existsByName(name)) {
            throw new ResourceConflictException("Indicator already exists: " + name);
        }

        Indicator def = new Indicator();
        def.setName(name);
        def.setParamSchema(json.toJson(request.getParamSchema()));
        def.setActive(request.getActive() == null || request.getActive());
        def = indicatorRepository.save(def);

        log.info("CREATE indicator '{}' id={} params={}",
                def.getName(), def.getId(), request.getParamSchema().keySet());
        return toResponse(def);
    }

    @Override
    @Transactional
    public IndicatorResponse update(UUID id, IndicatorRequest request) {
        Indicator def = requireIndicator(id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        List<String> errors = new ArrayList<>();
        String name = validator.normalizeIndicatorName(request.getName());
        if (name != null && name.length() > NAME_MAX) {
            errors.add("name must be at most " + NAME_MAX + " characters");
        }
        if (request.getParamSchema() != null) {
            errors.addAll(validator.validateParamSchema(request.getParamSchema()));
        }
        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }

        List<String> users = strategiesUsing(def.getName());

        if (name != null && !name.equals(def.getName())) {
            // Rule trees reference indicators by name, not by id, so a rename
            // would silently orphan every tree that mentions the old one.
            if (!users.isEmpty()) {
                throw new ResourceConflictException("Indicator '" + def.getName()
                        + "' cannot be renamed while referenced by strategy rule trees: " + users);
            }
            if (indicatorRepository.existsByName(name)) {
                throw new ResourceConflictException("Indicator already exists: " + name);
            }
            def.setName(name);
        }

        if (request.getParamSchema() != null) {
            // Narrowing the schema can invalidate a live rule tree; the strategy
            // validator would reject those trees on their next save, so the
            // removal is refused here instead of being discovered later.
            errors.addAll(checkSchemaStillSatisfies(def.getName(), request.getParamSchema().keySet()));
            if (!errors.isEmpty()) {
                throw new StrategyValidationException(errors);
            }
            def.setParamSchema(json.toJson(request.getParamSchema()));
        }
        if (request.getActive() != null) {
            if (!request.getActive() && !users.isEmpty()) {
                log.warn("Deactivating indicator '{}' still referenced by {}", def.getName(), users);
            }
            def.setActive(request.getActive());
        }

        indicatorRepository.save(def);
        log.info("UPDATE indicator '{}' id={} active={}", def.getName(), def.getId(), def.isActive());
        return toResponse(def);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Indicator def = requireIndicator(id);
        List<String> users = strategiesUsing(def.getName());
        if (!users.isEmpty()) {
            throw new ResourceConflictException("Indicator '" + def.getName()
                    + "' is referenced by the rule tree of " + users
                    + " and cannot be deleted. Deactivate it instead (PUT /api/v1/indicators/"
                    + id + " with \"active\": false).");
        }
        // user_strategy_indicators holds a real FK to this row, so a tuned usage
        // would make the delete a constraint violation. Refusing it here is the
        // same answer, said in a sentence the caller can act on.
        long tunings = userStrategyIndicatorRepository.countByIndicator_Id(id);
        if (tunings > 0) {
            throw new ResourceConflictException("Indicator '" + def.getName() + "' is tuned by "
                    + tunings + " user strategy(ies) and cannot be deleted. "
                    + "Deactivate it instead (PUT /api/v1/indicators/" + id + " with \"active\": false).");
        }
        indicatorRepository.delete(def);
        log.info("DELETE indicator '{}' id={}", def.getName(), id);
    }

    // -------------------------------------------------------------- helpers

    private Indicator requireIndicator(UUID id) {
        return indicatorRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Indicator", id));
    }

    /**
     * Strategies referencing this indicator by name.
     *
     * There is no join table to query - the relationship lives inside each
     * strategy's rule tree - so this scans the strategy catalog. That table holds
     * one row per strategy template, not per user configuration, so it stays
     * small by construction.
     */
    private List<String> strategiesUsing(String indicatorName) {
        List<String> names = new ArrayList<>();
        for (StrategyTemplate strategy : strategyRepository.findAllByOrderByNameAsc()) {
            JsonNode tree = json.readTree(strategy.getRuleTree());
            if (tree != null && IndicatorResolver.indicatorNames(tree).contains(indicatorName)) {
                names.add(strategy.getName());
            }
        }
        return names;
    }

    /** Rejects removing a schema parameter that a live rule tree still passes. */
    private List<String> checkSchemaStillSatisfies(String indicatorName, java.util.Set<String> allowed) {
        List<String> errors = new ArrayList<>();
        for (StrategyTemplate strategy : strategyRepository.findAllByOrderByNameAsc()) {
            JsonNode tree = json.readTree(strategy.getRuleTree());
            if (tree == null) {
                continue;
            }
            collectUsedParams(tree, indicatorName, allowed, strategy.getName(), errors);
        }
        return errors;
    }

    private void collectUsedParams(JsonNode node, String indicatorName, java.util.Set<String> allowed,
                                   String strategyName, List<String> errors) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectUsedParams(child, indicatorName, allowed, strategyName, errors));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (node.hasNonNull("ind") && indicatorName.equals(node.get("ind").asText())) {
            JsonNode params = node.get("params");
            if (params != null && params.isObject()) {
                params.properties().forEach(entry -> {
                    if (!allowed.contains(entry.getKey())) {
                        errors.add("paramSchema cannot drop '" + entry.getKey() + "': strategy '"
                                + strategyName + "' passes it to " + indicatorName);
                    }
                });
            }
        }
        node.properties().forEach(entry ->
                collectUsedParams(entry.getValue(), indicatorName, allowed, strategyName, errors));
    }

    private IndicatorResponse toResponse(Indicator def) {
        return new IndicatorResponse(
                def.getId(),
                def.getName(),
                json.toMap(def.getParamSchema()),
                def.isActive(),
                strategiesUsing(def.getName()),
                def.getCreatedAt());
    }
}

package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.StrategyDetailResponse;
import com.example.tradeLedger.dto.StrategyParamDefRequest;
import com.example.tradeLedger.dto.StrategyParamDefResponse;
import com.example.tradeLedger.dto.StrategyRequest;
import com.example.tradeLedger.entity.IndicatorDef;
import com.example.tradeLedger.entity.Strategy;
import com.example.tradeLedger.entity.StrategyParamDef;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.repository.IndicatorDefRepository;
import com.example.tradeLedger.repository.StrategyInstanceRepository;
import com.example.tradeLedger.repository.StrategyParamDefRepository;
import com.example.tradeLedger.repository.StrategyRepository;
import com.example.tradeLedger.service.StrategyDefinitionService;
import com.example.tradeLedger.utils.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StrategyDefinitionServiceImpl implements StrategyDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(StrategyDefinitionServiceImpl.class);

    private static final int NAME_MAX = 100;

    private final StrategyRepository strategyRepository;
    private final StrategyParamDefRepository paramDefRepository;
    private final StrategyInstanceRepository instanceRepository;
    private final IndicatorDefRepository indicatorDefRepository;
    private final StrategyDefinitionValidator validator;
    private final StrategyIndicatorSync indicatorSync;
    private final JsonSupport json;

    public StrategyDefinitionServiceImpl(StrategyRepository strategyRepository,
                                         StrategyParamDefRepository paramDefRepository,
                                         StrategyInstanceRepository instanceRepository,
                                         IndicatorDefRepository indicatorDefRepository,
                                         StrategyDefinitionValidator validator,
                                         StrategyIndicatorSync indicatorSync,
                                         JsonSupport json) {
        this.strategyRepository = strategyRepository;
        this.paramDefRepository = paramDefRepository;
        this.instanceRepository = instanceRepository;
        this.indicatorDefRepository = indicatorDefRepository;
        this.validator = validator;
        this.indicatorSync = indicatorSync;
        this.json = json;
    }

    // -------------------------------------------------------------- queries

    @Override
    @Transactional(readOnly = true)
    public List<StrategyDetailResponse> list(Boolean active, String search) {
        boolean filtered = search != null && !search.isBlank();
        List<Strategy> strategies;
        if (active == null && !filtered) {
            strategies = strategyRepository.findAllByOrderByNameAsc();
        } else if (active == null) {
            strategies = strategyRepository.findByNameContainingIgnoreCaseOrderByNameAsc(search.trim());
        } else if (!filtered) {
            strategies = strategyRepository.findByActiveOrderByNameAsc(active);
        } else {
            strategies = strategyRepository
                    .findByActiveAndNameContainingIgnoreCaseOrderByNameAsc(active, search.trim());
        }
        return strategies.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StrategyDetailResponse get(UUID id) {
        return toResponse(requireStrategy(id));
    }

    @Override
    @Transactional(readOnly = true)
    public StrategyDetailResponse getByName(String name) {
        Strategy strategy = strategyRepository.findByName(name)
                .orElseThrow(() -> ResourceNotFoundException.of("Strategy", name));
        return toResponse(strategy);
    }

    // --------------------------------------------------------------- create

    @Override
    @Transactional
    public StrategyDetailResponse create(StrategyRequest request) {
        List<String> errors = new ArrayList<>();
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        String name = trimToNull(request.getName());
        if (name == null) {
            errors.add("name is required");
        } else if (name.length() > NAME_MAX) {
            errors.add("name must be at most " + NAME_MAX + " characters");
        }
        if (request.getVersion() != null && request.getVersion() < 1) {
            errors.add("version must be >= 1");
        }

        List<StrategyParamDefRequest> params = request.getParams() == null ? List.of() : request.getParams();
        errors.addAll(validateParamSet(params));
        // A strategy may be created before its knobs exist; only check bindings
        // against a knob set that was actually supplied.
        errors.addAll(validator.validateRuleTree(request.getRuleTree(),
                params.isEmpty() ? null : paramKeys(params)));

        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }
        if (strategyRepository.existsByName(name)) {
            throw new ResourceConflictException("Strategy already exists: " + name);
        }

        Strategy strategy = new Strategy();
        strategy.setName(name);
        strategy.setDescription(request.getDescription());
        strategy.setVersion(request.getVersion() != null ? request.getVersion() : 1);
        strategy.setActive(request.getActive() == null || request.getActive());
        // Strategies authored through the API are never system rows - is_system is
        // what protects the seeded templates from being edited away.
        strategy.setSystem(false);
        strategy.setRuleTree(json.toJson(request.getRuleTree()));
        strategy = strategyRepository.save(strategy);

        for (StrategyParamDefRequest param : params) {
            paramDefRepository.save(newParamDef(strategy, param));
        }
        indicatorSync.sync(strategy);

        log.info("CREATE strategy '{}' id={} params={} indicators={}",
                strategy.getName(), strategy.getId(), params.size(),
                IndicatorResolver.indicatorNames(json.toNode(request.getRuleTree())));
        return toResponse(strategy);
    }

    // --------------------------------------------------------------- update

    @Override
    @Transactional
    public StrategyDetailResponse update(UUID id, StrategyRequest request) {
        Strategy strategy = requireStrategy(id);
        requireEditable(strategy);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }

        List<String> errors = new ArrayList<>();
        String name = trimToNull(request.getName());
        if (name != null && name.length() > NAME_MAX) {
            errors.add("name must be at most " + NAME_MAX + " characters");
        }
        if (request.getVersion() != null && request.getVersion() < 1) {
            errors.add("version must be >= 1");
        }

        boolean replacingParams = request.getParams() != null;
        List<StrategyParamDefRequest> params = replacingParams ? request.getParams() : List.of();
        if (replacingParams) {
            errors.addAll(validateParamSet(params));
        }

        // The rule tree and the knob set constrain each other, so whichever of the
        // two is changing is checked against the other's post-update state.
        Set<String> effectiveKeys = replacingParams ? paramKeys(params) : existingParamKeys(id);
        if (request.getRuleTree() != null) {
            errors.addAll(validator.validateRuleTree(request.getRuleTree(), effectiveKeys));
        } else if (replacingParams) {
            errors.addAll(validator.validateRuleTree(json.toMap(strategy.getRuleTree()), effectiveKeys));
        }

        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }
        if (name != null && !name.equals(strategy.getName())) {
            if (strategyRepository.existsByName(name)) {
                throw new ResourceConflictException("Strategy already exists: " + name);
            }
            strategy.setName(name);
        }

        if (request.getDescription() != null) {
            strategy.setDescription(request.getDescription());
        }
        if (request.getVersion() != null) {
            strategy.setVersion(request.getVersion());
        }
        if (request.getActive() != null) {
            strategy.setActive(request.getActive());
        }
        if (request.getRuleTree() != null) {
            strategy.setRuleTree(json.toJson(request.getRuleTree()));
        }
        strategyRepository.save(strategy);

        if (replacingParams) {
            replaceParams(strategy, params);
        }
        // Unconditional: the tree may not have changed, but an indicator it
        // references could have been created since the last save.
        indicatorSync.sync(strategy);

        log.info("UPDATE strategy '{}' id={} active={} paramsReplaced={}",
                strategy.getName(), strategy.getId(), strategy.isActive(), replacingParams);
        return toResponse(strategy);
    }

    /**
     * The knob set is replaced key by key rather than deleted and re-inserted, so
     * that ids stay stable for anything referencing them and so a partial failure
     * cannot leave a strategy with no parameters at all.
     */
    private void replaceParams(Strategy strategy, List<StrategyParamDefRequest> params) {
        List<StrategyParamDef> existing =
                paramDefRepository.findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(strategy.getId());
        Set<String> submitted = paramKeys(params);

        existing.stream()
                .filter(def -> !submitted.contains(def.getParameterKey()))
                .forEach(paramDefRepository::delete);

        for (StrategyParamDefRequest request : params) {
            StrategyParamDef def = existing.stream()
                    .filter(d -> d.getParameterKey().equals(request.getParameterKey().trim()))
                    .findFirst()
                    .orElseGet(() -> newParamDef(strategy, request));
            applyParam(def, request);
            paramDefRepository.save(def);
        }
    }

    // --------------------------------------------------------------- delete

    @Override
    @Transactional
    public void delete(UUID id) {
        Strategy strategy = requireStrategy(id);
        requireEditable(strategy);

        long instances = instanceRepository.countByStrategy_Id(id);
        if (instances > 0) {
            throw new ResourceConflictException("Strategy '" + strategy.getName() + "' is referenced by "
                    + instances + " strategy instance(s) and cannot be deleted. "
                    + "Deactivate it instead (PUT /api/v1/strategies/" + id + " with \"active\": false).");
        }
        // strategy_param_defs.strategy_id is ON DELETE CASCADE; the delete is done
        // explicitly so it also happens under ddl-auto-generated schemas.
        paramDefRepository.findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(id)
                .forEach(paramDefRepository::delete);
        // Same for the derived indicator index - its FK would block the delete.
        indicatorSync.clear(id);
        strategyRepository.delete(strategy);
        log.info("DELETE strategy '{}' id={}", strategy.getName(), id);
    }

    // --------------------------------------------------- parameter defs CRUD

    @Override
    @Transactional(readOnly = true)
    public List<StrategyParamDefResponse> listParams(UUID strategyId) {
        requireStrategy(strategyId);
        return paramDefRepository.findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(strategyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public StrategyParamDefResponse addParam(UUID strategyId, StrategyParamDefRequest request) {
        Strategy strategy = requireStrategy(strategyId);
        requireEditable(strategy);

        Set<String> siblings = existingParamKeys(strategyId);
        if (request != null && request.getParameterKey() != null) {
            siblings.add(request.getParameterKey().trim());
        }
        List<String> errors = validator.validateParamDef(request, siblings);
        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }

        String key = request.getParameterKey().trim();
        if (paramDefRepository.existsByStrategy_IdAndParameterKey(strategyId, key)) {
            throw new ResourceConflictException(
                    "Parameter '" + key + "' already exists on strategy '" + strategy.getName() + "'");
        }

        StrategyParamDef def = paramDefRepository.save(newParamDef(strategy, request));
        log.info("ADD param '{}' ({} scope) to strategy '{}'", key, def.getScope(), strategy.getName());
        return toResponse(def);
    }

    @Override
    @Transactional
    public StrategyParamDefResponse updateParam(UUID strategyId, Long paramId, StrategyParamDefRequest request) {
        Strategy strategy = requireStrategy(strategyId);
        requireEditable(strategy);

        StrategyParamDef def = paramDefRepository.findByIdAndStrategy_Id(paramId, strategyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Strategy parameter", paramId));

        if (request != null && request.getParameterKey() == null) {
            request.setParameterKey(def.getParameterKey());
        }
        if (request != null && request.getDataType() == null) {
            request.setDataType(def.getDataType());
        }
        if (request != null && request.getScope() == null) {
            request.setScope(def.getScope());
        }

        Set<String> siblings = existingParamKeys(strategyId);
        siblings.remove(def.getParameterKey());
        siblings.add(request.getParameterKey().trim());

        List<String> errors = validator.validateParamDef(request, siblings);
        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }

        String newKey = request.getParameterKey().trim();
        if (!newKey.equals(def.getParameterKey())
                && paramDefRepository.existsByStrategy_IdAndParameterKey(strategyId, newKey)) {
            throw new ResourceConflictException(
                    "Parameter '" + newKey + "' already exists on strategy '" + strategy.getName() + "'");
        }

        applyParam(def, request);
        paramDefRepository.save(def);
        log.info("UPDATE param '{}' on strategy '{}'", def.getParameterKey(), strategy.getName());
        return toResponse(def);
    }

    @Override
    @Transactional
    public void deleteParam(UUID strategyId, Long paramId) {
        Strategy strategy = requireStrategy(strategyId);
        requireEditable(strategy);

        StrategyParamDef def = paramDefRepository.findByIdAndStrategy_Id(paramId, strategyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Strategy parameter", paramId));

        // Removing a knob the rule tree still binds would leave the strategy
        // unresolvable, so the tree is consulted before the row goes.
        JsonNode tree = json.readTree(strategy.getRuleTree());
        if (tree != null && IndicatorResolver.bindings(tree).contains(def.getParameterKey())) {
            throw new ResourceConflictException("Parameter '" + def.getParameterKey()
                    + "' is bound by the rule tree of strategy '" + strategy.getName()
                    + "' and cannot be removed while the tree references $" + def.getParameterKey());
        }

        paramDefRepository.delete(def);
        log.info("DELETE param '{}' from strategy '{}'", def.getParameterKey(), strategy.getName());
    }

    // -------------------------------------------------------------- helpers

    private Strategy requireStrategy(UUID id) {
        return strategyRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Strategy", id));
    }

    /** Seeded templates are shared by every subscriber, so the API never mutates them. */
    private void requireEditable(Strategy strategy) {
        if (strategy.isSystem()) {
            throw new ResourceConflictException("Strategy '" + strategy.getName()
                    + "' is a system strategy and cannot be modified or deleted");
        }
    }

    private List<String> validateParamSet(List<StrategyParamDefRequest> params) {
        List<String> errors = new ArrayList<>();
        Set<String> keys = paramKeys(params);
        if (keys.size() != params.size()) {
            errors.add("params contains duplicate parameterKey values");
        }
        for (StrategyParamDefRequest param : params) {
            errors.addAll(validator.validateParamDef(param, keys));
        }
        return errors;
    }

    private Set<String> paramKeys(List<StrategyParamDefRequest> params) {
        Set<String> keys = new LinkedHashSet<>();
        for (StrategyParamDefRequest param : params) {
            if (param != null && param.getParameterKey() != null) {
                keys.add(param.getParameterKey().trim());
            }
        }
        return keys;
    }

    private Set<String> existingParamKeys(UUID strategyId) {
        Set<String> keys = new LinkedHashSet<>();
        paramDefRepository.findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(strategyId)
                .forEach(def -> keys.add(def.getParameterKey()));
        return keys;
    }

    private StrategyParamDef newParamDef(Strategy strategy, StrategyParamDefRequest request) {
        StrategyParamDef def = new StrategyParamDef();
        def.setStrategy(strategy);
        applyParam(def, request);
        return def;
    }

    private void applyParam(StrategyParamDef def, StrategyParamDefRequest request) {
        def.setParameterKey(request.getParameterKey().trim());
        def.setDataType(request.getDataType());
        def.setScope(request.getScope());
        def.setDefaultValue(request.getDefaultValue());
        def.setValidation(request.getValidation() == null ? null : json.toJson(request.getValidation()));
        def.setDisplayLabel(request.getDisplayLabel());
        if (request.getDisplayOrder() != null) {
            def.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getRequired() != null) {
            def.setRequired(request.getRequired());
        }
    }

    // -------------------------------------------------------------- mapping

    private StrategyDetailResponse toResponse(Strategy strategy) {
        List<StrategyParamDefResponse> params =
                paramDefRepository.findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(strategy.getId())
                        .stream().map(this::toResponse).toList();

        // Ids come from the strategy_indicators rows written at save time; the
        // names still come from the tree, so the two stay index-aligned even when
        // a referenced indicator is missing or inactive and therefore has no id.
        Map<String, UUID> mappedIds = indicatorSync.indicatorIdsByName(strategy.getId());

        List<String> known = new ArrayList<>();
        List<UUID> knownIds = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        JsonNode tree = json.readTree(strategy.getRuleTree());
        if (tree != null) {
            for (String name : IndicatorResolver.indicatorNames(tree)) {
                boolean usable = indicatorDefRepository.findByName(name)
                        .map(IndicatorDef::isActive)
                        .orElse(false);
                if (usable) {
                    known.add(name);
                    knownIds.add(mappedIds.get(name));
                } else {
                    unknown.add(name);
                }
            }
        }

        return new StrategyDetailResponse(
                strategy.getId(),
                strategy.getName(),
                strategy.getVersion(),
                strategy.getDescription(),
                strategy.isSystem(),
                strategy.isActive(),
                json.toMap(strategy.getRuleTree()),
                known,
                knownIds,
                unknown,
                params,
                instanceRepository.countByStrategy_Id(strategy.getId()),
                strategy.getCreatedAt(),
                strategy.getUpdatedAt());
    }

    private StrategyParamDefResponse toResponse(StrategyParamDef def) {
        return new StrategyParamDefResponse(
                def.getId(),
                def.getParameterKey(),
                def.getDataType(),
                def.getScope(),
                def.getDefaultValue(),
                json.toMap(def.getValidation()),
                def.getDisplayLabel(),
                def.getDisplayOrder(),
                def.isRequired());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

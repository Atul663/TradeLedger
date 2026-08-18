package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.IndicatorSummaryResponse;
import com.example.tradeLedger.dto.ParameterResponse;
import com.example.tradeLedger.dto.StrategyTemplateDetailResponse;
import com.example.tradeLedger.dto.StrategyParamDefinitionRequest;
import com.example.tradeLedger.dto.StrategyParamDefinitionResponse;
import com.example.tradeLedger.dto.StrategyTemplateRequest;
import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.IndicatorParameterLink;
import com.example.tradeLedger.entity.Parameter;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.entity.StrategyParamDefinition;
import com.example.tradeLedger.entity.StrategyParameterLink;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.repository.IndicatorRepository;
import com.example.tradeLedger.repository.IndicatorParameterLinkRepository;
import com.example.tradeLedger.repository.StrategyIndicatorLinkRepository;
import com.example.tradeLedger.repository.SharedStrategyConfigRepository;
import com.example.tradeLedger.repository.StrategyParamDefinitionRepository;
import com.example.tradeLedger.repository.StrategyParameterLinkRepository;
import com.example.tradeLedger.repository.StrategyTemplateRepository;
import com.example.tradeLedger.repository.UserStrategyRepository;
import com.example.tradeLedger.service.StrategyTemplateService;
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
public class StrategyTemplateServiceImpl implements StrategyTemplateService {

    private static final Logger log = LoggerFactory.getLogger(StrategyTemplateServiceImpl.class);

    private static final int NAME_MAX = 100;

    private final StrategyTemplateRepository strategyRepository;
    private final StrategyParamDefinitionRepository paramDefRepository;
    private final SharedStrategyConfigRepository sharedConfigRepository;
    private final UserStrategyRepository userStrategyRepository;
    private final IndicatorRepository indicatorRepository;
    private final StrategyIndicatorLinkRepository strategyIndicatorRepository;
    private final IndicatorParameterLinkRepository indicatorParameterRepository;
    private final StrategyParameterLinkRepository strategyParameterRepository;
    private final StrategyTemplateValidator validator;
    private final StrategyIndicatorLinkSync indicatorSync;
    private final StrategyParameterLinkSync parameterSync;
    private final JsonSupport json;

    public StrategyTemplateServiceImpl(StrategyTemplateRepository strategyRepository,
                                       StrategyParamDefinitionRepository paramDefRepository,
                                       SharedStrategyConfigRepository sharedConfigRepository,
                                       UserStrategyRepository userStrategyRepository,
                                       IndicatorRepository indicatorRepository,
                                       StrategyIndicatorLinkRepository strategyIndicatorRepository,
                                       IndicatorParameterLinkRepository indicatorParameterRepository,
                                       StrategyParameterLinkRepository strategyParameterRepository,
                                       StrategyTemplateValidator validator,
                                       StrategyIndicatorLinkSync indicatorSync,
                                       StrategyParameterLinkSync parameterSync,
                                       JsonSupport json) {
        this.strategyRepository = strategyRepository;
        this.paramDefRepository = paramDefRepository;
        this.sharedConfigRepository = sharedConfigRepository;
        this.userStrategyRepository = userStrategyRepository;
        this.indicatorRepository = indicatorRepository;
        this.strategyIndicatorRepository = strategyIndicatorRepository;
        this.indicatorParameterRepository = indicatorParameterRepository;
        this.strategyParameterRepository = strategyParameterRepository;
        this.validator = validator;
        this.indicatorSync = indicatorSync;
        this.parameterSync = parameterSync;
        this.json = json;
    }

    // -------------------------------------------------------------- queries

    @Override
    @Transactional(readOnly = true)
    public List<StrategyTemplateDetailResponse> list(Boolean active, String search) {
        boolean filtered = search != null && !search.isBlank();
        List<StrategyTemplate> strategies;
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
    public StrategyTemplateDetailResponse get(UUID id) {
        return toResponse(requireStrategy(id));
    }

    @Override
    @Transactional(readOnly = true)
    public StrategyTemplateDetailResponse getByName(String name) {
        StrategyTemplate strategy = strategyRepository.findByName(name)
                .orElseThrow(() -> ResourceNotFoundException.of("Strategy template", name));
        return toResponse(strategy);
    }

    // --------------------------------------------------------------- create

    @Override
    @Transactional
    public StrategyTemplateDetailResponse create(StrategyTemplateRequest request) {
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

        List<StrategyParamDefinitionRequest> params = request.getParams() == null ? List.of() : request.getParams();
        errors.addAll(validateParamSet(params));
        // A strategy may be created before its knobs exist; only check bindings
        // against a knob set that was actually supplied.
        errors.addAll(validator.validateRuleTree(request.getRuleTree(),
                params.isEmpty() ? null : paramKeys(params)));

        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }
        if (strategyRepository.existsByName(name)) {
            throw new ResourceConflictException("StrategyTemplate already exists: " + name);
        }

        StrategyTemplate strategy = new StrategyTemplate();
        strategy.setName(name);
        strategy.setDescription(request.getDescription());
        strategy.setVersion(request.getVersion() != null ? request.getVersion() : 1);
        strategy.setActive(request.getActive() == null || request.getActive());
        // Strategies authored through the API are never system rows - is_system is
        // what protects the seeded templates from being edited away.
        strategy.setSystem(false);
        strategy.setRuleTree(json.toJson(request.getRuleTree()));
        strategy = strategyRepository.save(strategy);

        for (StrategyParamDefinitionRequest param : params) {
            paramDefRepository.save(newParamDef(strategy, param));
        }
        // Index the rule tree into strategy_indicator_links, then derive the knob set
        // from the catalog. Order matters: the parameter sync walks the indicator
        // links the first call writes.
        indicatorSync.sync(strategy);
        parameterSync.sync(strategy);

        log.info("CREATE strategy '{}' id={} params={} indicators={}",
                strategy.getName(), strategy.getId(), params.size(),
                IndicatorResolver.indicatorNames(json.toNode(request.getRuleTree())));
        return toResponse(strategy);
    }

    // --------------------------------------------------------------- update

    @Override
    @Transactional
    public StrategyTemplateDetailResponse update(UUID id, StrategyTemplateRequest request) {
        StrategyTemplate strategy = requireStrategy(id);
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
        List<StrategyParamDefinitionRequest> params = replacingParams ? request.getParams() : List.of();
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
                throw new ResourceConflictException("StrategyTemplate already exists: " + name);
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
        parameterSync.sync(strategy);

        log.info("UPDATE strategy '{}' id={} active={} paramsReplaced={}",
                strategy.getName(), strategy.getId(), strategy.isActive(), replacingParams);
        return toResponse(strategy);
    }

    /**
     * The knob set is replaced key by key rather than deleted and re-inserted, so
     * that ids stay stable for anything referencing them and so a partial failure
     * cannot leave a strategy with no parameters at all.
     */
    private void replaceParams(StrategyTemplate strategy, List<StrategyParamDefinitionRequest> params) {
        List<StrategyParamDefinition> existing =
                paramDefRepository.findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(strategy.getId());
        Set<String> submitted = paramKeys(params);

        existing.stream()
                .filter(def -> !submitted.contains(def.getParameterKey()))
                .forEach(paramDefRepository::delete);

        for (StrategyParamDefinitionRequest request : params) {
            StrategyParamDefinition def = existing.stream()
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
        StrategyTemplate strategy = requireStrategy(id);
        requireEditable(strategy);

        long instances = sharedConfigRepository.countByStrategy_Id(id);
        if (instances > 0) {
            throw new ResourceConflictException("Strategy template '" + strategy.getName() + "' is referenced by "
                    + instances + " strategy instance(s) and cannot be deleted. "
                    + "Deactivate it instead (PUT /api/v1/strategy-templates/" + id + " with \"active\": false).");
        }
        long saves = userStrategyRepository.countByStrategy_Id(id);
        if (saves > 0) {
            throw new ResourceConflictException("Strategy template '" + strategy.getName() + "' is saved by "
                    + saves + " user(s) and cannot be deleted. "
                    + "Deactivate it instead (PUT /api/v1/strategy-templates/" + id + " with \"active\": false).");
        }
        // strategy_param_definitions.strategy_id is ON DELETE CASCADE; the delete is done
        // explicitly so it also happens under ddl-auto-generated schemas.
        paramDefRepository.findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(id)
                .forEach(paramDefRepository::delete);
        // Same for the link tables - their FKs would block the delete.
        indicatorSync.clear(id);
        parameterSync.clear(id);
        strategyRepository.delete(strategy);
        log.info("DELETE strategy '{}' id={}", strategy.getName(), id);
    }

    // --------------------------------------------------- parameter defs CRUD

    @Override
    @Transactional(readOnly = true)
    public List<StrategyParamDefinitionResponse> listParams(UUID strategyId) {
        requireStrategy(strategyId);
        return paramDefRepository.findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(strategyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public StrategyParamDefinitionResponse addParam(UUID strategyId, StrategyParamDefinitionRequest request) {
        StrategyTemplate strategy = requireStrategy(strategyId);
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

        StrategyParamDefinition def = paramDefRepository.save(newParamDef(strategy, request));
        log.info("ADD param '{}' ({} scope) to strategy '{}'", key, def.getScope(), strategy.getName());
        return toResponse(def);
    }

    @Override
    @Transactional
    public StrategyParamDefinitionResponse updateParam(UUID strategyId, Long paramId, StrategyParamDefinitionRequest request) {
        StrategyTemplate strategy = requireStrategy(strategyId);
        requireEditable(strategy);

        StrategyParamDefinition def = paramDefRepository.findByIdAndStrategy_Id(paramId, strategyId)
                .orElseThrow(() -> ResourceNotFoundException.of("StrategyTemplate parameter", paramId));

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
        StrategyTemplate strategy = requireStrategy(strategyId);
        requireEditable(strategy);

        StrategyParamDefinition def = paramDefRepository.findByIdAndStrategy_Id(paramId, strategyId)
                .orElseThrow(() -> ResourceNotFoundException.of("StrategyTemplate parameter", paramId));

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

    private StrategyTemplate requireStrategy(UUID id) {
        return strategyRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Strategy template", id));
    }

    /** Seeded templates are shared by every subscriber, so the API never mutates them. */
    private void requireEditable(StrategyTemplate strategy) {
        if (strategy.isSystem()) {
            throw new ResourceConflictException("Strategy template '" + strategy.getName()
                    + "' is a system strategy and cannot be modified or deleted");
        }
    }

    private List<String> validateParamSet(List<StrategyParamDefinitionRequest> params) {
        List<String> errors = new ArrayList<>();
        Set<String> keys = paramKeys(params);
        if (keys.size() != params.size()) {
            errors.add("params contains duplicate parameterKey values");
        }
        for (StrategyParamDefinitionRequest param : params) {
            errors.addAll(validator.validateParamDef(param, keys));
        }
        return errors;
    }

    private Set<String> paramKeys(List<StrategyParamDefinitionRequest> params) {
        Set<String> keys = new LinkedHashSet<>();
        for (StrategyParamDefinitionRequest param : params) {
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

    private StrategyParamDefinition newParamDef(StrategyTemplate strategy, StrategyParamDefinitionRequest request) {
        StrategyParamDefinition def = new StrategyParamDefinition();
        def.setStrategy(strategy);
        applyParam(def, request);
        return def;
    }

    private void applyParam(StrategyParamDefinition def, StrategyParamDefinitionRequest request) {
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

    private StrategyTemplateDetailResponse toResponse(StrategyTemplate strategy) {
        // The hierarchy comes from the link tables, not the rule tree: one row per
        // indicator the strategy uses, each carrying its own parameters by id.
        List<IndicatorSummaryResponse> indicators = strategyIndicatorRepository
                .findByStrategy_IdOrderByIndicator_NameAsc(strategy.getId()).stream()
                .map(link -> new IndicatorSummaryResponse(
                        link.getIndicator().getId(),
                        link.getIndicator().getName(),
                        link.getIndicator().isActive(),
                        indicatorParameterRepository
                                .findByIndicator_IdOrderByDisplayOrderAscIdAsc(link.getIndicator().getId())
                                .stream().map(this::toResponse).toList()))
                .toList();

        List<ParameterResponse> parameters = strategyParameterRepository
                .findByStrategy_IdOrderByDisplayOrderAscIdAsc(strategy.getId()).stream()
                .map(this::toResponse)
                .toList();

        // A name the tree references that resolves to no active indicator has no
        // link row and therefore no id - which is exactly how a broken tree
        // surfaces before anyone subscribes to it.
        List<String> unknown = new ArrayList<>();
        JsonNode tree = json.readTree(strategy.getRuleTree());
        if (tree != null) {
            for (String name : IndicatorResolver.indicatorNames(tree)) {
                boolean usable = indicatorRepository.findByName(name)
                        .map(Indicator::isActive)
                        .orElse(false);
                if (!usable) {
                    unknown.add(name);
                }
            }
        }

        return new StrategyTemplateDetailResponse(
                strategy.getId(),
                strategy.getName(),
                strategy.getVersion(),
                strategy.getDescription(),
                strategy.isSystem(),
                strategy.isActive(),
                json.toMap(strategy.getRuleTree()),
                indicators,
                parameters,
                unknown,
                sharedConfigRepository.countByStrategy_Id(strategy.getId()),
                strategy.getCreatedAt(),
                strategy.getUpdatedAt());
    }

    private ParameterResponse toResponse(IndicatorParameterLink link) {
        return toResponse(link.getParameter(), link.effectiveDefault(),
                link.effectiveValidation(), link.getDisplayOrder(), link.isRequired());
    }

    private ParameterResponse toResponse(StrategyParameterLink link) {
        return toResponse(link.getParameter(), link.effectiveDefault(),
                link.effectiveValidation(), link.getDisplayOrder(), link.isRequired());
    }

    /** Catalog identity plus the values in force for this particular attachment. */
    private ParameterResponse toResponse(Parameter parameter, String defaultValue,
                                         String validation, int displayOrder, boolean required) {
        return new ParameterResponse(
                parameter.getId(),
                parameter.getCode(),
                parameter.getName(),
                parameter.getDataType(),
                parameter.getScope(),
                defaultValue,
                json.toMap(validation),
                parameter.getDescription(),
                parameter.isUniversal(),
                displayOrder,
                required);
    }

    private StrategyParamDefinitionResponse toResponse(StrategyParamDefinition def) {
        return new StrategyParamDefinitionResponse(
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

package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.IndicatorSummaryResponse;
import com.example.tradeLedger.dto.StrategyTemplateDetailResponse;
import com.example.tradeLedger.dto.StrategyTemplateIndicatorGroupResponse;
import com.example.tradeLedger.dto.StrategyTemplateRequest;
import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.indicator.IndicatorResolver;
import com.example.tradeLedger.repository.IndicatorRepository;
import com.example.tradeLedger.repository.SharedStrategyConfigRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD over the template catalog.
 *
 * Much smaller than it was: a template is a name, a description and a rule tree.
 * There is no knob set to author, no link table to reconcile and no derived
 * definition table to keep in sync - the indicators the tree names declare their
 * own parameters, and every other setting a strategy has is a column on
 * {@code user_strategies}.
 */
@Service
public class StrategyTemplateServiceImpl implements StrategyTemplateService {

    private static final Logger log = LoggerFactory.getLogger(StrategyTemplateServiceImpl.class);

    private static final int NAME_MAX = 100;

    private final StrategyTemplateRepository strategyRepository;
    private final SharedStrategyConfigRepository sharedConfigRepository;
    private final UserStrategyRepository userStrategyRepository;
    private final IndicatorRepository indicatorRepository;
    private final StrategyTemplateValidator validator;
    private final StrategyFixedParameters fixedParameters;
    private final JsonSupport json;

    public StrategyTemplateServiceImpl(StrategyTemplateRepository strategyRepository,
                                       SharedStrategyConfigRepository sharedConfigRepository,
                                       UserStrategyRepository userStrategyRepository,
                                       IndicatorRepository indicatorRepository,
                                       StrategyTemplateValidator validator,
                                       StrategyFixedParameters fixedParameters,
                                       JsonSupport json) {
        this.strategyRepository = strategyRepository;
        this.sharedConfigRepository = sharedConfigRepository;
        this.userStrategyRepository = userStrategyRepository;
        this.indicatorRepository = indicatorRepository;
        this.validator = validator;
        this.fixedParameters = fixedParameters;
        this.json = json;
    }

    // -------------------------------------------------------------- queries

    @Override
    @Transactional(readOnly = true)
    public List<StrategyTemplateDetailResponse> list(Boolean active, String search) {
        boolean filtered = search != null && !search.isBlank();
        List<StrategyTemplate> templates;
        if (active == null && !filtered) {
            templates = strategyRepository.findAllByOrderByNameAsc();
        } else if (active == null) {
            templates = strategyRepository.findByNameContainingIgnoreCaseOrderByNameAsc(search.trim());
        } else if (!filtered) {
            templates = strategyRepository.findByActiveOrderByNameAsc(active);
        } else {
            templates = strategyRepository
                    .findByActiveAndNameContainingIgnoreCaseOrderByNameAsc(active, search.trim());
        }
        return templates.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StrategyTemplateDetailResponse get(UUID id) {
        return toResponse(requireTemplate(id));
    }

    @Override
    @Transactional(readOnly = true)
    public StrategyTemplateDetailResponse getByName(String name) {
        return toResponse(strategyRepository.findByName(name)
                .orElseThrow(() -> ResourceNotFoundException.of("Strategy template", name)));
    }

    // --------------------------------------------------------------- create

    @Override
    @Transactional
    public StrategyTemplateDetailResponse create(StrategyTemplateRequest request) {
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        List<String> errors = new ArrayList<>();
        String name = trimToNull(request.getName());
        if (name == null) {
            errors.add("name is required");
        } else if (name.length() > NAME_MAX) {
            errors.add("name must be at most " + NAME_MAX + " characters");
        }
        if (request.getVersion() != null && request.getVersion() < 1) {
            errors.add("version must be >= 1");
        }
        errors.addAll(validator.validateRuleTree(request.getRuleTree()));

        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }
        if (strategyRepository.existsByName(name)) {
            throw new ResourceConflictException("Strategy template already exists: " + name);
        }

        StrategyTemplate template = new StrategyTemplate();
        template.setName(name);
        template.setDescription(request.getDescription());
        template.setVersion(request.getVersion() != null ? request.getVersion() : 1);
        template.setActive(request.getActive() == null || request.getActive());
        // Templates authored through the API are never system rows - is_system is
        // what protects the seeded ones from being edited away.
        template.setSystem(false);
        template.setRuleTree(json.toJson(request.getRuleTree()));
        template = strategyRepository.save(template);

        log.info("CREATE template {} id={} indicators={}", template.getName(), template.getId(),
                IndicatorResolver.indicatorNames(json.toNode(request.getRuleTree())));
        return toResponse(template);
    }

    // --------------------------------------------------------------- update

    @Override
    @Transactional
    public StrategyTemplateDetailResponse update(UUID id, StrategyTemplateRequest request) {
        StrategyTemplate template = requireTemplate(id);
        requireEditable(template);
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
        if (request.getRuleTree() != null) {
            errors.addAll(validator.validateRuleTree(request.getRuleTree()));
        }
        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }

        if (name != null && !name.equals(template.getName())) {
            if (strategyRepository.existsByName(name)) {
                throw new ResourceConflictException("Strategy template already exists: " + name);
            }
            template.setName(name);
        }
        if (request.getDescription() != null) {
            template.setDescription(request.getDescription());
        }
        if (request.getVersion() != null) {
            template.setVersion(request.getVersion());
        }
        if (request.getActive() != null) {
            template.setActive(request.getActive());
        }
        if (request.getRuleTree() != null) {
            // Changing the tree changes which indicators a strategy built from this
            // template would carry, so it is refused once any exist: their indicator
            // rows and their hashed values were settled under the old tree.
            long strategies = userStrategyRepository.countByStrategy_Id(id);
            if (strategies > 0 && !json.toJson(request.getRuleTree()).equals(template.getRuleTree())) {
                throw new ResourceConflictException("Template " + template.getName() + " is used by "
                        + strategies + " strategy(ies); its rule tree cannot change under them. "
                        + "Publish a new template instead.");
            }
            template.setRuleTree(json.toJson(request.getRuleTree()));
        }
        strategyRepository.save(template);

        log.info("UPDATE template {} id={} active={}", template.getName(), template.getId(), template.isActive());
        return toResponse(template);
    }

    // --------------------------------------------------------------- delete

    @Override
    @Transactional
    public void delete(UUID id) {
        StrategyTemplate template = requireTemplate(id);
        requireEditable(template);

        long instances = sharedConfigRepository.countByStrategy_Id(id);
        if (instances > 0) {
            throw new ResourceConflictException("Strategy template " + template.getName()
                    + " is referenced by " + instances + " shared computation(s) and cannot be deleted. "
                    + "Deactivate it instead (PUT /api/v1/strategy-templates/" + id + " with active false).");
        }
        long strategies = userStrategyRepository.countByStrategy_Id(id);
        if (strategies > 0) {
            throw new ResourceConflictException("Strategy template " + template.getName()
                    + " is used by " + strategies + " user strategy(ies) and cannot be deleted. "
                    + "Deactivate it instead (PUT /api/v1/strategy-templates/" + id + " with active false).");
        }
        strategyRepository.delete(template);
        log.info("DELETE template {} id={}", template.getName(), id);
    }

    // -------------------------------------------------------------- helpers

    private StrategyTemplate requireTemplate(UUID id) {
        return strategyRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Strategy template", id));
    }

    /** Seeded templates are shared by every user, so the API never mutates them. */
    private void requireEditable(StrategyTemplate template) {
        if (template.isSystem()) {
            throw new ResourceConflictException("Strategy template " + template.getName()
                    + " is a system template and cannot be modified or deleted");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // -------------------------------------------------------------- mapping

    /**
     * The indicator list is read from the rule tree itself, which is the only
     * declaration there is. A name that resolves to no active indicator has no id
     * and lands in {@code unknownIndicators} - which is how a broken tree surfaces
     * before anyone builds a strategy on it.
     */
    private StrategyTemplateDetailResponse toResponse(StrategyTemplate template) {
        List<IndicatorSummaryResponse> indicators = new ArrayList<>();
        List<StrategyTemplateIndicatorGroupResponse> groups = new ArrayList<>();
        List<String> unknown = new ArrayList<>();

        JsonNode tree = json.readTree(template.getRuleTree());
        if (tree != null) {
            // The tree resolves to distinct names, so a group holds one indicator;
            // what varies is how many NODES named it, and that is the number a
            // builder form needs - it is how many tuning rows it has to draw.
            for (Map.Entry<String, Integer> named : IndicatorResolver.indicatorNameCounts(tree).entrySet()) {
                String name = named.getKey();
                Indicator indicator = indicatorRepository.findByNameIgnoreCase(name).orElse(null);
                if (indicator == null || !indicator.isActive()) {
                    unknown.add(name);
                    continue;
                }
                IndicatorSummaryResponse summary = new IndicatorSummaryResponse(
                        indicator.getId(),
                        indicator.getName(),
                        indicator.isActive(),
                        IndicatorParams.labelled(json.toMap(indicator.getParamSchema())));
                indicators.add(summary);
                groups.add(new StrategyTemplateIndicatorGroupResponse(
                        indicator.getName(), named.getValue(), 1, List.of(summary)));
            }
        }

        return new StrategyTemplateDetailResponse(
                template.getId(),
                template.getName(),
                template.getVersion(),
                template.getDescription(),
                template.isSystem(),
                template.isActive(),
                json.toMap(template.getRuleTree()),
                indicators,
                groups,
                fixedParameters.descriptors(),
                unknown,
                sharedConfigRepository.countByStrategy_Id(template.getId()),
                userStrategyRepository.countByStrategy_Id(template.getId()),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}

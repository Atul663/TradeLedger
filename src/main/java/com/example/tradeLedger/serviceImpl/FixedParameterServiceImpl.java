package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.FixedParameterGroupResponse;
import com.example.tradeLedger.dto.FixedParameterRequest;
import com.example.tradeLedger.dto.FixedParameterResponse;
import com.example.tradeLedger.entity.FixedParameter;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.FixedParameterRepository;
import com.example.tradeLedger.service.FixedParameterService;
import com.example.tradeLedger.utils.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FixedParameterServiceImpl implements FixedParameterService {

    private static final Logger log = LoggerFactory.getLogger(FixedParameterServiceImpl.class);

    /**
     * A machine key, not a sentence: it is meant to be the API field name of the
     * column it describes, so a form can bind the two by string equality.
     */
    private static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_.]{0,99}$");

    /** Bounds apply to a number; a set of choices applies to an enum. */
    private static final Set<String> NUMERIC_TYPES =
            Set.of(FixedParameter.TYPE_INT, FixedParameter.TYPE_DECIMAL);

    private static final String KEY_MIN = "min";
    private static final String KEY_MAX = "max";
    private static final String KEY_OPTIONS = "options";

    private final FixedParameterRepository repository;
    private final JsonSupport json;

    public FixedParameterServiceImpl(FixedParameterRepository repository, JsonSupport json) {
        this.repository = repository;
        this.json = json;
    }

    // ------------------------------------------------------------------ read

    @Override
    @Transactional(readOnly = true)
    public List<FixedParameterResponse> list(String paramGroup, String scope, Boolean active) {
        String group = normalizeGroup(paramGroup);
        String wantedScope = scope == null || scope.isBlank()
                ? null
                : scope.trim().toLowerCase(Locale.ROOT);
        if (wantedScope != null && !FixedParameter.SCOPES.contains(wantedScope)) {
            throw new StrategyValidationException(
                    "scope must be one of " + FixedParameter.SCOPES + ", got " + scope);
        }

        List<FixedParameter> rows = active == null
                ? repository.findAllByOrderByParamGroupAscDisplayOrderAscNameAsc()
                : repository.findByActiveOrderByParamGroupAscDisplayOrderAscNameAsc(active);

        return rows.stream()
                .filter(row -> group == null || group.equalsIgnoreCase(row.getParamGroup()))
                .filter(row -> wantedScope == null || wantedScope.equals(row.getScope()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * The flat list folded into its sections.
     *
     * Built on top of {@link #list} rather than beside it, so the two can never
     * disagree about which rows a filter admits or what order they come in - the
     * list is already ordered by group, so consecutive rows of one group form a
     * run and a LinkedHashMap keeps the runs in catalog order.
     */
    @Override
    @Transactional(readOnly = true)
    public List<FixedParameterGroupResponse> listGrouped(String paramGroup, String scope,
                                                         Boolean active) {
        Map<String, List<FixedParameterResponse>> byGroup = new LinkedHashMap<>();
        for (FixedParameterResponse row : list(paramGroup, scope, active)) {
            byGroup.computeIfAbsent(row.paramGroup(), key -> new ArrayList<>()).add(row);
        }
        List<FixedParameterGroupResponse> groups = new ArrayList<>();
        byGroup.forEach((group, rows) ->
                groups.add(new FixedParameterGroupResponse(group, rows.size(), rows)));
        return groups;
    }

    @Override
    @Transactional(readOnly = true)
    public FixedParameterResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public FixedParameterResponse getByName(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            throw new StrategyValidationException("name is required");
        }
        return repository.findByNameIgnoreCase(trimmed)
                .map(this::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Fixed parameter", trimmed));
    }

    // ----------------------------------------------------------------- write

    @Override
    @Transactional
    public FixedParameterResponse create(FixedParameterRequest request) {
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        List<String> errors = new ArrayList<>();

        String name = trimToNull(request.getName());
        if (name == null) {
            errors.add("name is required");
        } else if (!NAME.matcher(name).matches()) {
            errors.add("name must start with a letter and contain only letters, digits, "
                    + "underscores or dots, got '" + name + "'");
        }
        String label = trimToNull(request.getLabel());
        if (label == null) {
            errors.add("label is required");
        } else if (label.length() > 100) {
            errors.add("label must be at most 100 characters");
        }

        String dataType;
        if (request.getDataType() == null || request.getDataType().isBlank()) {
            errors.add("dataType is required (" + FixedParameter.TYPES + ")");
            dataType = null;
        } else {
            dataType = normalizeDataType(request.getDataType(), errors);
        }
        String scope = normalizeScope(request.getScope(), errors);
        String group = normalizeGroup(request.getParamGroup());
        if (group != null && group.length() > 50) {
            errors.add("paramGroup must be at most 50 characters");
        }
        int displayOrder = normalizeDisplayOrder(request.getDisplayOrder(), errors);

        Map<String, Object> validation = checkValidation(dataType, request.getValidation(), errors);
        String defaultValue = checkDefault(dataType, trimToNull(request.getDefaultValue()),
                validation, errors);

        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ResourceConflictException("Fixed parameter already exists: " + name);
        }

        FixedParameter parameter = new FixedParameter();
        parameter.setName(name);
        parameter.setLabel(label);
        parameter.setDescription(trimToNull(request.getDescription()));
        parameter.setDataType(dataType);
        parameter.setScope(scope != null ? scope : FixedParameter.SCOPE_EXECUTION);
        parameter.setDefaultValue(defaultValue);
        parameter.setValidation(storedValidation(validation));
        parameter.setParamGroup(group);
        parameter.setDisplayOrder(displayOrder);
        parameter.setRequired(Boolean.TRUE.equals(request.getRequired()));
        parameter.setActive(request.getActive() == null || request.getActive());
        repository.save(parameter);

        log.info("CREATE fixed parameter {} type={} scope={} id={}",
                name, dataType, parameter.getScope(), parameter.getId());
        return toResponse(parameter);
    }

    @Override
    @Transactional
    public FixedParameterResponse update(UUID id, FixedParameterRequest request) {
        FixedParameter parameter = require(id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        List<String> errors = new ArrayList<>();

        String name = trimToNull(request.getName());
        if (name != null && !NAME.matcher(name).matches()) {
            errors.add("name must start with a letter and contain only letters, digits, "
                    + "underscores or dots, got '" + name + "'");
        }
        String label = trimToNull(request.getLabel());
        if (label != null && label.length() > 100) {
            errors.add("label must be at most 100 characters");
        }
        String scope = normalizeScope(request.getScope(), errors);
        String group = request.getParamGroup() == null
                ? parameter.getParamGroup()
                : normalizeGroup(request.getParamGroup());
        if (group != null && group.length() > 50) {
            errors.add("paramGroup must be at most 50 characters");
        }
        int displayOrder = request.getDisplayOrder() == null
                ? parameter.getDisplayOrder()
                : normalizeDisplayOrder(request.getDisplayOrder(), errors);

        // Judged against the RESULTING row, not the submitted fragment: retyping a
        // knob from decimal to enum has to fail while its stored default is still a
        // number, even though this request carries neither the default nor the
        // options.
        String dataType = request.getDataType() == null
                ? parameter.getDataType()
                : normalizeDataType(request.getDataType(), errors);

        Map<String, Object> validation = request.getValidation() == null
                ? json.toMap(parameter.getValidation())
                : request.getValidation();
        validation = checkValidation(dataType, validation, errors);

        String defaultInput = request.getDefaultValue() == null
                ? parameter.getDefaultValue()
                : trimToNull(request.getDefaultValue());
        String defaultValue = checkDefault(dataType, defaultInput, validation, errors);

        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }

        if (name != null) {
            // A pure recasing of the same key is a rename that cannot collide.
            if (!name.equalsIgnoreCase(parameter.getName())
                    && repository.existsByNameIgnoreCase(name)) {
                throw new ResourceConflictException("Fixed parameter already exists: " + name);
            }
            parameter.setName(name);
        }
        if (label != null) {
            parameter.setLabel(label);
        }
        if (request.getDescription() != null) {
            parameter.setDescription(trimToNull(request.getDescription()));
        }
        parameter.setDataType(dataType);
        if (scope != null) {
            parameter.setScope(scope);
        }
        parameter.setDefaultValue(defaultValue);
        parameter.setValidation(storedValidation(validation));
        parameter.setParamGroup(group);
        parameter.setDisplayOrder(displayOrder);
        if (request.getRequired() != null) {
            parameter.setRequired(request.getRequired());
        }
        if (request.getActive() != null) {
            parameter.setActive(request.getActive());
        }
        repository.save(parameter);

        log.info("UPDATE fixed parameter {} type={} active={} id={}",
                parameter.getName(), parameter.getDataType(), parameter.isActive(), id);
        return toResponse(parameter);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        FixedParameter parameter = require(id);
        repository.delete(parameter);
        log.info("DELETE fixed parameter {} id={}", parameter.getName(), id);
    }

    // ------------------------------------------------------------ validation

    private FixedParameter require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Fixed parameter", id));
    }

    private String normalizeDataType(String dataType, List<String> errors) {
        String normalized = dataType == null || dataType.isBlank()
                ? null
                : dataType.trim().toLowerCase(Locale.ROOT);
        if (normalized != null && !FixedParameter.TYPES.contains(normalized)) {
            errors.add("dataType must be one of " + FixedParameter.TYPES + ", got " + dataType);
            return null;
        }
        return normalized;
    }

    private String normalizeScope(String scope, List<String> errors) {
        String normalized = scope == null || scope.isBlank()
                ? null
                : scope.trim().toLowerCase(Locale.ROOT);
        if (normalized != null && !FixedParameter.SCOPES.contains(normalized)) {
            errors.add("scope must be one of " + FixedParameter.SCOPES + ", got " + scope);
            return null;
        }
        return normalized;
    }

    /** Lowercased so the ORDER BY groups the sections rather than their spellings. */
    private String normalizeGroup(String paramGroup) {
        String trimmed = trimToNull(paramGroup);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private int normalizeDisplayOrder(Integer displayOrder, List<String> errors) {
        if (displayOrder == null) {
            return 0;
        }
        if (displayOrder < 0) {
            errors.add("displayOrder must not be negative, got " + displayOrder);
            return 0;
        }
        return displayOrder;
    }

    /**
     * The bounds have to make sense for the type and for each other, or a form
     * renders a control nobody can satisfy.
     *
     * @return the rules to store, empty when there are none
     */
    private Map<String, Object> checkValidation(String dataType, Map<String, Object> validation,
                                                List<String> errors) {
        Map<String, Object> rules = new LinkedHashMap<>();
        if (validation != null) {
            validation.forEach((key, value) -> {
                if (value != null) {
                    rules.put(key, value);
                }
            });
        }
        BigDecimal min = numericRule(rules, KEY_MIN, errors);
        BigDecimal max = numericRule(rules, KEY_MAX, errors);
        if (min != null && max != null && min.compareTo(max) > 0) {
            errors.add("validation.min must not exceed validation.max, got "
                    + min.toPlainString() + " > " + max.toPlainString());
        }
        if (dataType == null) {
            // The type is already reported missing or unrecognized; judging the
            // rules against it would only add noise about a type nobody sent.
            return rules;
        }
        boolean isEnum = FixedParameter.TYPE_ENUM.equals(dataType);
        boolean isNumeric = NUMERIC_TYPES.contains(dataType);

        if (!isNumeric && (rules.containsKey(KEY_MIN) || rules.containsKey(KEY_MAX))) {
            errors.add("validation.min/max only apply to an int or decimal, not " + dataType);
        }

        Object options = rules.get(KEY_OPTIONS);
        if (options != null) {
            if (!isEnum) {
                errors.add("validation.options only applies to an enum, not " + dataType);
            } else if (!(options instanceof List<?> list) || list.isEmpty()) {
                errors.add("validation.options must be a non-empty array of choices");
            } else if (list.stream().anyMatch(choice -> choice == null || choice instanceof Map
                    || choice instanceof List)) {
                errors.add("validation.options must hold scalars, not objects or arrays");
            }
        } else if (isEnum) {
            errors.add("validation.options is required when dataType is enum");
        }
        return rules;
    }

    private BigDecimal numericRule(Map<String, Object> rules, String key, List<String> errors) {
        Object value = rules.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            errors.add("validation." + key + " must be a number, got " + value);
            return null;
        }
    }

    /**
     * The default is stored as text whatever the type is, so this is the only
     * thing standing between the catalog and a decimal knob that pre-fills "none".
     *
     * @return the default to store, normalized for its type
     */
    private String checkDefault(String dataType, String defaultValue,
                                Map<String, Object> validation, List<String> errors) {
        if (defaultValue == null || dataType == null) {
            return defaultValue;
        }
        switch (dataType) {
            case FixedParameter.TYPE_INT -> {
                try {
                    checkBounds(new BigDecimal(Long.parseLong(defaultValue)), validation, errors);
                } catch (NumberFormatException e) {
                    errors.add("defaultValue must be a whole number for an int knob, got '"
                            + defaultValue + "'");
                }
            }
            case FixedParameter.TYPE_DECIMAL -> {
                try {
                    checkBounds(new BigDecimal(defaultValue), validation, errors);
                } catch (NumberFormatException e) {
                    errors.add("defaultValue must be a number for a decimal knob, got '"
                            + defaultValue + "'");
                }
            }
            case FixedParameter.TYPE_BOOL -> {
                if (!"true".equalsIgnoreCase(defaultValue) && !"false".equalsIgnoreCase(defaultValue)) {
                    errors.add("defaultValue must be true or false for a bool knob, got '"
                            + defaultValue + "'");
                    return defaultValue;
                }
                return defaultValue.toLowerCase(Locale.ROOT);
            }
            case FixedParameter.TYPE_ENUM -> {
                Object options = validation == null ? null : validation.get(KEY_OPTIONS);
                if (options instanceof List<?> list && !list.isEmpty()
                        && list.stream().noneMatch(choice -> String.valueOf(choice).equals(defaultValue))) {
                    errors.add("defaultValue must be one of validation.options " + list
                            + ", got '" + defaultValue + "'");
                }
            }
            case FixedParameter.TYPE_TIMEFRAME -> {
                // Timeframes is the one place that decides what a timeframe looks
                // like; a second copy of the rule here could disagree with the
                // column this knob describes.
                try {
                    return Timeframes.normalizeOrNull(defaultValue);
                } catch (StrategyValidationException e) {
                    errors.add("defaultValue: " + e.getMessage());
                }
            }
            default -> {
                // text takes whatever it is given.
            }
        }
        return defaultValue;
    }

    private void checkBounds(BigDecimal value, Map<String, Object> validation, List<String> errors) {
        if (validation == null) {
            return;
        }
        List<String> ignored = new ArrayList<>();
        BigDecimal min = numericRule(validation, KEY_MIN, ignored);
        BigDecimal max = numericRule(validation, KEY_MAX, ignored);
        if (min != null && value.compareTo(min) < 0) {
            errors.add("defaultValue " + value.toPlainString() + " is below validation.min "
                    + min.toPlainString());
        }
        if (max != null && value.compareTo(max) > 0) {
            errors.add("defaultValue " + value.toPlainString() + " is above validation.max "
                    + max.toPlainString());
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

    /** An empty rule set is stored as NULL, not as {@code {}} - an unbounded knob. */
    private String storedValidation(Map<String, Object> validation) {
        return validation == null || validation.isEmpty() ? null : json.toJson(validation);
    }

    private FixedParameterResponse toResponse(FixedParameter parameter) {
        return new FixedParameterResponse(
                parameter.getId(),
                parameter.getName(),
                parameter.getLabel(),
                parameter.getDescription(),
                parameter.getDataType(),
                parameter.getScope(),
                parameter.getDefaultValue(),
                json.toMap(parameter.getValidation()),
                parameter.getParamGroup(),
                parameter.getDisplayOrder(),
                parameter.isRequired(),
                parameter.isActive(),
                parameter.getCreatedAt(),
                parameter.getUpdatedAt());
    }
}

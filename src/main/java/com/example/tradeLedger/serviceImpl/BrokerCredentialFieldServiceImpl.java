package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.BrokerCredentialFieldRequest;
import com.example.tradeLedger.dto.BrokerCredentialFieldResponse;
import com.example.tradeLedger.entity.Broker;
import com.example.tradeLedger.entity.BrokerCredentialField;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.BrokerCredentialFieldRepository;
import com.example.tradeLedger.repository.BrokerRepository;
import com.example.tradeLedger.service.BrokerCredentialFieldService;
import com.example.tradeLedger.utils.JsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class BrokerCredentialFieldServiceImpl implements BrokerCredentialFieldService {

    private static final Logger log = LoggerFactory.getLogger(BrokerCredentialFieldServiceImpl.class);

    private static final String KEY_MIN_LENGTH = "minLength";
    private static final String KEY_MAX_LENGTH = "maxLength";
    private static final String KEY_PATTERN = "pattern";

    private static final int LABEL_MAX = 100;
    private static final int PLACEHOLDER_MAX = 200;

    private final BrokerCredentialFieldRepository repository;
    private final BrokerRepository brokers;
    private final JsonSupport json;

    public BrokerCredentialFieldServiceImpl(BrokerCredentialFieldRepository repository,
                                            BrokerRepository brokers,
                                            JsonSupport json) {
        this.repository = repository;
        this.brokers = brokers;
        this.json = json;
    }

    // ------------------------------------------------------------------ read

    @Override
    @Transactional(readOnly = true)
    public List<BrokerCredentialFieldResponse> list(UUID brokerId, String brokerCode, String group,
                                                    Boolean active) {
        String wantedGroup = trimToNull(group) == null
                ? null
                : group.trim().toLowerCase(Locale.ROOT);
        if (wantedGroup != null && !BrokerCredentialField.GROUPS.contains(wantedGroup)) {
            throw new StrategyValidationException(
                    "group must be one of " + BrokerCredentialField.GROUPS + ", got " + group);
        }
        String code = trimToNull(brokerCode);

        // The broker filter goes to the database, because it is the one with an
        // index behind it; the group is applied here, over a handful of rows.
        List<BrokerCredentialField> rows;
        if (brokerId != null) {
            rows = active == null
                    ? repository.findByBrokerIdOrderByFieldGroupAscDisplayOrderAscFieldKeyAsc(brokerId)
                    : repository.findByBrokerIdAndActiveOrderByFieldGroupAscDisplayOrderAscFieldKeyAsc(
                            brokerId, active);
        } else if (code != null) {
            rows = active == null
                    ? repository.findByBrokerCodeIgnoreCaseOrderByFieldGroupAscDisplayOrderAscFieldKeyAsc(code)
                    : repository.findByBrokerCodeIgnoreCaseAndActiveOrderByFieldGroupAscDisplayOrderAscFieldKeyAsc(
                            code, active);
        } else {
            rows = repository.findAllByOrderByBrokerNameAscFieldGroupAscDisplayOrderAscFieldKeyAsc()
                    .stream()
                    .filter(row -> active == null || active == row.isActive())
                    .toList();
        }

        return rows.stream()
                .filter(row -> wantedGroup == null || wantedGroup.equals(row.getFieldGroup()))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BrokerCredentialFieldResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public BrokerCredentialFieldResponse getByBrokerAndKey(UUID brokerId, String fieldKey) {
        if (brokerId == null) {
            throw new StrategyValidationException("brokerId is required");
        }
        String key = trimToNull(fieldKey);
        if (key == null) {
            throw new StrategyValidationException("fieldKey is required");
        }
        return repository.findByBrokerIdAndFieldKeyIgnoreCase(brokerId, key)
                .map(this::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Broker credential field",
                        brokerId + "/" + key));
    }

    // ----------------------------------------------------------------- write

    @Override
    @Transactional
    public BrokerCredentialFieldResponse create(BrokerCredentialFieldRequest request) {
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        List<String> errors = new ArrayList<>();

        Broker broker = resolveBroker(request, null, errors);

        String fieldKey = normalizeFieldKey(request.getFieldKey(), errors, true);
        String label = trimToNull(request.getLabel());
        if (label == null) {
            errors.add("label is required");
        } else if (label.length() > LABEL_MAX) {
            errors.add("label must be at most " + LABEL_MAX + " characters");
        }

        String dataType = request.getDataType() == null
                ? BrokerCredentialField.TYPE_TEXT
                : normalizeDataType(request.getDataType(), errors);
        String fieldGroup = request.getFieldGroup() == null
                ? BrokerCredentialField.GROUP_CREDENTIALS
                : normalizeGroup(request.getFieldGroup(), errors);
        int displayOrder = normalizeDisplayOrder(request.getDisplayOrder(), errors);
        String placeholder = trimToNull(request.getPlaceholder());
        if (placeholder != null && placeholder.length() > PLACEHOLDER_MAX) {
            errors.add("placeholder must be at most " + PLACEHOLDER_MAX + " characters");
        }
        String helpUrl = checkUrl("helpUrl", trimToNull(request.getHelpUrl()), errors);

        Map<String, Object> validation = checkValidation(request.getValidation(), errors);
        String defaultValue = checkDefault(dataType, trimToNull(request.getDefaultValue()), errors);

        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }
        if (repository.existsByBrokerIdAndFieldKeyIgnoreCase(broker.getId(), fieldKey)) {
            throw new ResourceConflictException(
                    "Broker credential field already exists: " + broker.getCode() + "/" + fieldKey);
        }

        BrokerCredentialField field = new BrokerCredentialField();
        field.setBroker(broker);
        field.setFieldKey(fieldKey);
        field.setLabel(label);
        field.setDescription(trimToNull(request.getDescription()));
        field.setPlaceholder(placeholder);
        field.setDataType(dataType);
        field.setDefaultValue(defaultValue);
        field.setValidation(storedValidation(validation));
        field.setFieldGroup(fieldGroup);
        field.setDisplayOrder(displayOrder);
        field.setRequired(request.getRequired() == null || request.getRequired());
        field.setUserSupplied(request.getUserSupplied() == null || request.getUserSupplied());
        field.setHelpUrl(helpUrl);
        field.setActive(request.getActive() == null || request.getActive());
        repository.save(field);

        log.info("CREATE broker credential field {}/{} type={} id={}",
                broker.getCode(), fieldKey, dataType, field.getId());
        return toResponse(field);
    }

    @Override
    @Transactional
    public BrokerCredentialFieldResponse update(UUID id, BrokerCredentialFieldRequest request) {
        BrokerCredentialField field = require(id);
        if (request == null) {
            throw new StrategyValidationException("Request body is required");
        }
        List<String> errors = new ArrayList<>();

        Broker broker = resolveBroker(request, field.getBroker(), errors);
        String fieldKey = request.getFieldKey() == null
                ? field.getFieldKey()
                : normalizeFieldKey(request.getFieldKey(), errors, true);

        String label = trimToNull(request.getLabel());
        if (label != null && label.length() > LABEL_MAX) {
            errors.add("label must be at most " + LABEL_MAX + " characters");
        }
        String dataType = request.getDataType() == null
                ? field.getDataType()
                : normalizeDataType(request.getDataType(), errors);
        String fieldGroup = request.getFieldGroup() == null
                ? field.getFieldGroup()
                : normalizeGroup(request.getFieldGroup(), errors);
        int displayOrder = request.getDisplayOrder() == null
                ? field.getDisplayOrder()
                : normalizeDisplayOrder(request.getDisplayOrder(), errors);
        String placeholder = request.getPlaceholder() == null
                ? field.getPlaceholder()
                : trimToNull(request.getPlaceholder());
        if (placeholder != null && placeholder.length() > PLACEHOLDER_MAX) {
            errors.add("placeholder must be at most " + PLACEHOLDER_MAX + " characters");
        }
        String helpUrl = request.getHelpUrl() == null
                ? field.getHelpUrl()
                : checkUrl("helpUrl", trimToNull(request.getHelpUrl()), errors);

        Map<String, Object> validation = request.getValidation() == null
                ? json.toMap(field.getValidation())
                : request.getValidation();
        validation = checkValidation(validation, errors);

        // Judged against the RESULTING row, not the submitted fragment: retyping a
        // field to secret has to fail while a default is still stored on it, even
        // though this request carries only the type.
        String defaultInput = request.getDefaultValue() == null
                ? field.getDefaultValue()
                : trimToNull(request.getDefaultValue());
        String defaultValue = checkDefault(dataType, defaultInput, errors);

        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }

        boolean rebinding = !broker.getId().equals(field.getBroker().getId())
                || !fieldKey.equalsIgnoreCase(field.getFieldKey());
        if (rebinding && repository.existsByBrokerIdAndFieldKeyIgnoreCase(broker.getId(), fieldKey)) {
            throw new ResourceConflictException(
                    "Broker credential field already exists: " + broker.getCode() + "/" + fieldKey);
        }

        field.setBroker(broker);
        field.setFieldKey(fieldKey);
        if (label != null) {
            field.setLabel(label);
        }
        if (request.getDescription() != null) {
            field.setDescription(trimToNull(request.getDescription()));
        }
        field.setPlaceholder(placeholder);
        field.setDataType(dataType);
        field.setDefaultValue(defaultValue);
        field.setValidation(storedValidation(validation));
        field.setFieldGroup(fieldGroup);
        field.setDisplayOrder(displayOrder);
        if (request.getRequired() != null) {
            field.setRequired(request.getRequired());
        }
        if (request.getUserSupplied() != null) {
            field.setUserSupplied(request.getUserSupplied());
        }
        field.setHelpUrl(helpUrl);
        if (request.getActive() != null) {
            field.setActive(request.getActive());
        }
        repository.save(field);

        log.info("UPDATE broker credential field {}/{} type={} active={} id={}",
                broker.getCode(), field.getFieldKey(), field.getDataType(), field.isActive(), id);
        return toResponse(field);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        BrokerCredentialField field = require(id);
        repository.delete(field);
        log.info("DELETE broker credential field {}/{} id={}",
                field.getBroker().getCode(), field.getFieldKey(), id);
    }

    // ------------------------------------------------------------ validation

    private BrokerCredentialField require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Broker credential field", id));
    }

    /**
     * The broker by id, or by code for a caller that has not resolved one.
     *
     * A missing broker is reported as a validation error rather than thrown on the
     * spot, so a request that also has a bad field key gets told about both at
     * once. {@code current} keeps a partial update from having to resend it.
     */
    private Broker resolveBroker(BrokerCredentialFieldRequest request, Broker current,
                                 List<String> errors) {
        if (request.getBrokerId() != null) {
            return brokers.findById(request.getBrokerId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Broker", request.getBrokerId()));
        }
        String code = trimToNull(request.getBrokerCode());
        if (code != null) {
            return brokers.findByCodeIgnoreCase(code)
                    .orElseThrow(() -> ResourceNotFoundException.of("Broker", code));
        }
        if (current != null) {
            return current;
        }
        errors.add("brokerId or brokerCode is required");
        return null;
    }

    /**
     * The key has to name a column {@code broker_credentials} actually has, or the
     * input it renders binds to nothing and fails at save time. The same list the
     * table CHECKs, reported here with the alternatives.
     */
    private String normalizeFieldKey(String fieldKey, List<String> errors, boolean requireIt) {
        String normalized = fieldKey == null || fieldKey.isBlank()
                ? null
                : fieldKey.trim().toLowerCase(Locale.ROOT);
        if (normalized == null) {
            if (requireIt) {
                errors.add("fieldKey is required (" + BrokerCredentialField.FIELD_KEYS + ")");
            }
            return null;
        }
        if (!BrokerCredentialField.FIELD_KEYS.contains(normalized)) {
            errors.add("fieldKey must name a broker_credentials column, one of "
                    + BrokerCredentialField.FIELD_KEYS + ", got " + fieldKey);
            return null;
        }
        return normalized;
    }

    private String normalizeDataType(String dataType, List<String> errors) {
        String normalized = dataType == null || dataType.isBlank()
                ? null
                : dataType.trim().toLowerCase(Locale.ROOT);
        if (normalized != null && !BrokerCredentialField.TYPES.contains(normalized)) {
            errors.add("dataType must be one of " + BrokerCredentialField.TYPES
                    + ", got " + dataType);
            return null;
        }
        return normalized;
    }

    private String normalizeGroup(String fieldGroup, List<String> errors) {
        String normalized = fieldGroup == null || fieldGroup.isBlank()
                ? null
                : fieldGroup.trim().toLowerCase(Locale.ROOT);
        if (normalized != null && !BrokerCredentialField.GROUPS.contains(normalized)) {
            errors.add("fieldGroup must be one of " + BrokerCredentialField.GROUPS
                    + ", got " + fieldGroup);
            return null;
        }
        return normalized;
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
     * The bounds have to be satisfiable, or a form renders a box nobody can fill.
     *
     * @return the rules to store, empty when there are none
     */
    private Map<String, Object> checkValidation(Map<String, Object> validation,
                                                List<String> errors) {
        Map<String, Object> rules = new LinkedHashMap<>();
        if (validation != null) {
            validation.forEach((key, value) -> {
                if (value != null) {
                    rules.put(key, value);
                }
            });
        }
        Integer min = lengthRule(rules, KEY_MIN_LENGTH, errors);
        Integer max = lengthRule(rules, KEY_MAX_LENGTH, errors);
        if (min != null && max != null && min > max) {
            errors.add("validation.minLength must not exceed validation.maxLength, got "
                    + min + " > " + max);
        }
        Object pattern = rules.get(KEY_PATTERN);
        if (pattern != null) {
            try {
                Pattern.compile(pattern.toString());
            } catch (PatternSyntaxException e) {
                errors.add("validation.pattern is not a valid regular expression: " + pattern);
            }
        }
        return rules;
    }

    private Integer lengthRule(Map<String, Object> rules, String key, List<String> errors) {
        Object value = rules.get(key);
        if (value == null) {
            return null;
        }
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException e) {
                errors.add("validation." + key + " must be a whole number, got " + value);
                return null;
            }
        }
        if (parsed < 0) {
            errors.add("validation." + key + " must not be negative, got " + parsed);
            return null;
        }
        return parsed;
    }

    /**
     * <b>A descriptor must never carry a credential.</b> Everything else in this
     * table is public onboarding metadata stored in plaintext; a default on a
     * secret field would be a working secret sitting unencrypted in a catalog the
     * whole platform can read.
     */
    private String checkDefault(String dataType, String defaultValue, List<String> errors) {
        if (defaultValue == null || dataType == null) {
            return defaultValue;
        }
        if (BrokerCredentialField.TYPE_SECRET.equals(dataType)) {
            errors.add("defaultValue is not allowed on a secret field - a descriptor must never "
                    + "carry a credential");
            return null;
        }
        if (BrokerCredentialField.TYPE_URL.equals(dataType)) {
            return checkUrl("defaultValue", defaultValue, errors);
        }
        return defaultValue;
    }

    /** A callback the user is shown has to be one a browser will follow. */
    private String checkUrl(String field, String url, List<String> errors) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            errors.add(field + " must start with http:// or https://, got " + url);
            return null;
        }
        return url;
    }

    // ----------------------------------------------------------------- shape

    /** Empty rules are stored as null, so an unbounded field has no jsonb to read. */
    private String storedValidation(Map<String, Object> validation) {
        return validation == null || validation.isEmpty() ? null : json.toJson(validation);
    }

    private BrokerCredentialFieldResponse toResponse(BrokerCredentialField field) {
        Broker broker = field.getBroker();
        return new BrokerCredentialFieldResponse(
                field.getId(),
                broker != null ? broker.getId() : null,
                broker != null ? broker.getCode() : null,
                broker != null ? broker.getName() : null,
                field.getFieldKey(),
                field.getLabel(),
                field.getDescription(),
                field.getPlaceholder(),
                field.getDataType(),
                field.getDefaultValue(),
                json.toMap(field.getValidation()),
                field.getFieldGroup(),
                field.getDisplayOrder(),
                field.isRequired(),
                field.isUserSupplied(),
                field.getHelpUrl(),
                field.isActive(),
                field.getCreatedAt(),
                field.getUpdatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

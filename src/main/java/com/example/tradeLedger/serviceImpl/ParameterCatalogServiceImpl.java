package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.ParameterResponse;
import com.example.tradeLedger.dto.ParameterUsageResponse;
import com.example.tradeLedger.entity.IndicatorParameterLink;
import com.example.tradeLedger.entity.Parameter;
import com.example.tradeLedger.entity.StrategyParameterLink;
import com.example.tradeLedger.exception.ResourceNotFoundException;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.repository.IndicatorRepository;
import com.example.tradeLedger.repository.IndicatorParameterLinkRepository;
import com.example.tradeLedger.repository.ParameterRepository;
import com.example.tradeLedger.repository.StrategyParameterLinkRepository;
import com.example.tradeLedger.repository.StrategyTemplateRepository;
import com.example.tradeLedger.service.ParameterCatalogService;
import com.example.tradeLedger.utils.JsonSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ParameterCatalogServiceImpl implements ParameterCatalogService {

    private static final Set<String> SCOPES = Set.of(Parameter.SCOPE_SIGNAL, Parameter.SCOPE_EXECUTION);

    private final ParameterRepository parameterRepository;
    private final IndicatorParameterLinkRepository indicatorParameterRepository;
    private final StrategyParameterLinkRepository strategyParameterRepository;
    private final IndicatorRepository indicatorRepository;
    private final StrategyTemplateRepository strategyRepository;
    private final JsonSupport json;

    public ParameterCatalogServiceImpl(ParameterRepository parameterRepository,
                                       IndicatorParameterLinkRepository indicatorParameterRepository,
                                       StrategyParameterLinkRepository strategyParameterRepository,
                                       IndicatorRepository indicatorRepository,
                                       StrategyTemplateRepository strategyRepository,
                                       JsonSupport json) {
        this.parameterRepository = parameterRepository;
        this.indicatorParameterRepository = indicatorParameterRepository;
        this.strategyParameterRepository = strategyParameterRepository;
        this.indicatorRepository = indicatorRepository;
        this.strategyRepository = strategyRepository;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParameterResponse> list(String scope) {
        if (scope == null || scope.isBlank()) {
            return parameterRepository.findAllByOrderByDisplayOrderAscCodeAsc().stream().map(this::toResponse).toList();
        }
        String normalized = scope.trim().toLowerCase(Locale.ROOT);
        if (!SCOPES.contains(normalized)) {
            throw new StrategyValidationException("scope must be one of " + SCOPES);
        }
        return parameterRepository.findByScopeOrderByDisplayOrderAscCodeAsc(normalized).stream()
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ParameterResponse get(Long id) {
        return toResponse(requireParameter(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ParameterResponse getByCode(String code) {
        Parameter parameter = parameterRepository.findByCode(code.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> ResourceNotFoundException.of("Parameter", code));
        return toResponse(parameter);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParameterResponse> forIndicator(UUID indicatorId) {
        if (!indicatorRepository.existsById(indicatorId)) {
            throw ResourceNotFoundException.of("Indicator", indicatorId);
        }
        return indicatorParameterRepository.findByIndicator_IdOrderByDisplayOrderAscIdAsc(indicatorId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParameterResponse> forStrategy(UUID strategyId) {
        if (!strategyRepository.existsById(strategyId)) {
            throw ResourceNotFoundException.of("Strategy template", strategyId);
        }
        return strategyParameterRepository.findByStrategy_IdOrderByDisplayOrderAscIdAsc(strategyId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ParameterUsageResponse usage(Long id) {
        Parameter parameter = requireParameter(id);

        List<ParameterUsageResponse.Usage> indicators =
                indicatorParameterRepository.findByParameter_IdOrderByIndicator_NameAsc(id).stream()
                        .map(link -> new ParameterUsageResponse.Usage(
                                link.getIndicator().getId(), link.getIndicator().getName()))
                        .toList();

        List<ParameterUsageResponse.Usage> strategies =
                strategyParameterRepository.findByParameter_IdOrderByStrategy_NameAsc(id).stream()
                        .map(link -> new ParameterUsageResponse.Usage(
                                link.getStrategy().getId(), link.getStrategy().getName()))
                        .toList();

        return new ParameterUsageResponse(parameter.getId(), parameter.getCode(), parameter.getName(),
                parameter.isUniversal(), indicators, strategies);
    }

    // -------------------------------------------------------------- mapping

    private Parameter requireParameter(Long id) {
        return parameterRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Parameter", id));
    }

    /** Catalog row on its own - no attachment, so the canonical values stand. */
    private ParameterResponse toResponse(Parameter parameter) {
        return build(parameter, parameter.getDefaultValue(), parameter.getValidation(),
                parameter.getDisplayOrder(), true);
    }

    private ParameterResponse toResponse(IndicatorParameterLink link) {
        return build(link.getParameter(), link.effectiveDefault(),
                link.effectiveValidation(), link.getDisplayOrder(), link.isRequired());
    }

    private ParameterResponse toResponse(StrategyParameterLink link) {
        return build(link.getParameter(), link.effectiveDefault(),
                link.effectiveValidation(), link.getDisplayOrder(), link.isRequired());
    }

    private ParameterResponse build(Parameter parameter, String defaultValue,
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
}

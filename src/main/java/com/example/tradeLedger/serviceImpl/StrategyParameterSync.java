package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.IndicatorParameter;
import com.example.tradeLedger.entity.Parameter;
import com.example.tradeLedger.entity.Strategy;
import com.example.tradeLedger.entity.StrategyIndicator;
import com.example.tradeLedger.entity.StrategyParamDef;
import com.example.tradeLedger.entity.StrategyParameter;
import com.example.tradeLedger.repository.IndicatorParameterRepository;
import com.example.tradeLedger.repository.ParameterRepository;
import com.example.tradeLedger.repository.StrategyIndicatorRepository;
import com.example.tradeLedger.repository.StrategyParamDefRepository;
import com.example.tradeLedger.repository.StrategyParameterRepository;
import com.example.tradeLedger.repository.StrategyRepository;
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
 * Turns the parameter catalog into the flat knob set the execution engine already
 * consumes.
 *
 * The catalog ({@code parameters} + {@code indicator_parameters} +
 * {@code strategy_parameters}) is where parameters are authored and where the
 * id-based hierarchy lives. {@code strategy_param_defs} is what
 * {@link StrategyParamValidator} validates against and what the config hash is
 * computed from. Rather than run those as two independent systems, the second is
 * DERIVED from the first - the same pattern {@link StrategyIndicatorSync} uses
 * for the strategy-indicator index.
 *
 * The effective knob set of a strategy is:
 * <ul>
 *   <li>every parameter of every indicator the strategy uses - signal scope,
 *       hashed, shared</li>
 *   <li>every parameter linked to the strategy directly - execution scope,
 *       personal, never hashed</li>
 * </ul>
 *
 * {@link #desiredDefs} is deliberately a pure function of its arguments so the
 * derivation can be tested without a database.
 */
@Component
public class StrategyParameterSync {

    private static final Logger log = LoggerFactory.getLogger(StrategyParameterSync.class);

    /** One derived knob, before it is reconciled against the stored rows. */
    public record DerivedDef(String parameterKey, String dataType, String scope, String defaultValue,
                             String validation, String displayLabel, int displayOrder, boolean required) {
    }

    private final StrategyRepository strategyRepository;
    private final StrategyIndicatorRepository strategyIndicatorRepository;
    private final IndicatorParameterRepository indicatorParameterRepository;
    private final StrategyParameterRepository strategyParameterRepository;
    private final StrategyParamDefRepository paramDefRepository;
    private final ParameterRepository parameterRepository;

    public StrategyParameterSync(StrategyRepository strategyRepository,
                                 StrategyIndicatorRepository strategyIndicatorRepository,
                                 IndicatorParameterRepository indicatorParameterRepository,
                                 StrategyParameterRepository strategyParameterRepository,
                                 StrategyParamDefRepository paramDefRepository,
                                 ParameterRepository parameterRepository) {
        this.strategyRepository = strategyRepository;
        this.strategyIndicatorRepository = strategyIndicatorRepository;
        this.indicatorParameterRepository = indicatorParameterRepository;
        this.strategyParameterRepository = strategyParameterRepository;
        this.paramDefRepository = paramDefRepository;
        this.parameterRepository = parameterRepository;
    }

    // ------------------------------------------------------------ derivation

    /**
     * The knob set a strategy should have, given its indicator links and its own
     * parameter links.
     *
     * Indicator parameters come first and keep their per-indicator ordering;
     * strategy parameters follow. A parameter reachable through two indicators
     * appears once - the code is the key, and two indicators asking for the same
     * catalog row are asking for the same knob.
     *
     * Pure: no repositories, no persistence context. This is the whole of the
     * catalog-to-engine mapping, and it is what the unit tests exercise.
     */
    public static List<DerivedDef> desiredDefs(List<IndicatorParameter> indicatorParams,
                                               List<StrategyParameter> strategyParams) {
        Map<String, DerivedDef> byKey = new LinkedHashMap<>();
        int order = 0;

        for (IndicatorParameter link : indicatorParams) {
            Parameter parameter = link.getParameter();
            order++;
            byKey.putIfAbsent(parameter.getCode(), new DerivedDef(
                    parameter.getCode(),
                    parameter.getDataType(),
                    parameter.getScope(),
                    link.effectiveDefault(),
                    link.effectiveValidation(),
                    parameter.getName(),
                    order,
                    link.isRequired()));
        }

        for (StrategyParameter link : strategyParams) {
            Parameter parameter = link.getParameter();
            order++;
            byKey.putIfAbsent(parameter.getCode(), new DerivedDef(
                    parameter.getCode(),
                    parameter.getDataType(),
                    parameter.getScope(),
                    link.effectiveDefault(),
                    link.effectiveValidation(),
                    parameter.getName(),
                    order,
                    link.isRequired()));
        }

        return new ArrayList<>(byKey.values());
    }

    // -------------------------------------------------------------- persist

    /**
     * Links every universal catalog parameter to this strategy, then rewrites the
     * strategy's {@code strategy_param_defs} to match the catalog.
     *
     * Reconciled key by key rather than deleted and re-inserted, so a knob that
     * survives keeps its id and no delete/insert pair can trip the unique
     * constraint inside one transaction.
     */
    @Transactional
    public List<DerivedDef> sync(Strategy strategy) {
        attachUniversalParameters(strategy);

        List<IndicatorParameter> indicatorParams = new ArrayList<>();
        for (StrategyIndicator link : strategyIndicatorRepository
                .findByStrategy_IdOrderByIndicator_NameAsc(strategy.getId())) {
            indicatorParams.addAll(indicatorParameterRepository
                    .findByIndicator_IdOrderByDisplayOrderAscIdAsc(link.getIndicator().getId()));
        }
        List<StrategyParameter> strategyParams =
                strategyParameterRepository.findByStrategy_IdOrderByDisplayOrderAscIdAsc(strategy.getId());

        List<DerivedDef> desired = desiredDefs(indicatorParams, strategyParams);
        applyToParamDefs(strategy, desired);
        return desired;
    }

    /**
     * Universal parameters are catalog rows flagged as belonging to every
     * strategy. The link rows are still written, so a strategy's parameter list is
     * uniform whether a parameter got there automatically or by hand.
     */
    private void attachUniversalParameters(Strategy strategy) {
        int order = 100;
        for (Parameter parameter : parameterRepository.findByUniversalTrueOrderByDisplayOrderAscCodeAsc()) {
            order++;
            if (strategyParameterRepository.existsByStrategy_IdAndParameter_Id(
                    strategy.getId(), parameter.getId())) {
                continue;
            }
            StrategyParameter link = new StrategyParameter();
            link.setStrategy(strategy);
            link.setParameter(parameter);
            link.setDisplayOrder(order);
            link.setRequired(true);
            strategyParameterRepository.save(link);
            log.info("Linked universal parameter '{}' to strategy '{}'",
                    parameter.getCode(), strategy.getName());
        }
    }

    private void applyToParamDefs(Strategy strategy, List<DerivedDef> desired) {
        List<StrategyParamDef> existing =
                paramDefRepository.findByStrategy_IdOrderByDisplayOrderAscParameterKeyAsc(strategy.getId());

        Set<String> wanted = new HashSet<>();
        desired.forEach(def -> wanted.add(def.parameterKey()));

        existing.stream()
                .filter(def -> !wanted.contains(def.getParameterKey()))
                .forEach(paramDefRepository::delete);

        Map<String, StrategyParamDef> byKey = new LinkedHashMap<>();
        existing.forEach(def -> byKey.put(def.getParameterKey(), def));

        for (DerivedDef want : desired) {
            StrategyParamDef def = byKey.get(want.parameterKey());
            if (def == null) {
                def = new StrategyParamDef();
                def.setStrategy(strategy);
                def.setParameterKey(want.parameterKey());
            }
            def.setDataType(want.dataType());
            def.setScope(want.scope());
            def.setDefaultValue(want.defaultValue());
            def.setValidation(want.validation());
            def.setDisplayLabel(want.displayLabel());
            def.setDisplayOrder(want.displayOrder());
            def.setRequired(want.required());
            paramDefRepository.save(def);
        }
    }

    /** Drops the strategy's parameter links; called before a strategy is deleted. */
    @Transactional
    public void clear(UUID strategyId) {
        strategyParameterRepository.deleteByStrategy_Id(strategyId);
    }

    /** Rebuilds the derived knob set for every strategy. Run once at startup. */
    @Transactional
    public void syncAll() {
        int knobs = 0;
        for (Strategy strategy : strategyRepository.findAllByOrderByNameAsc()) {
            knobs += sync(strategy).size();
        }
        log.info("Strategy parameter set rebuilt from catalog: {} knob(s)", knobs);
    }
}

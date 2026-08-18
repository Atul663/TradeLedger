package com.example.tradeLedger.config;

import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.IndicatorParameterLink;
import com.example.tradeLedger.entity.Parameter;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.entity.StrategyParamDefinition;
import com.example.tradeLedger.repository.IndicatorRepository;
import com.example.tradeLedger.repository.IndicatorParameterLinkRepository;
import com.example.tradeLedger.repository.ParameterRepository;
import com.example.tradeLedger.repository.SharedStrategyConfigRepository;
import com.example.tradeLedger.repository.StrategyTemplateRepository;
import com.example.tradeLedger.serviceImpl.StrategyIndicatorLinkSync;
import com.example.tradeLedger.serviceImpl.StrategyParameterLinkSync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the predefined catalog: the parameters, the indicators, the EMA Crossover
 * strategy, and the links that connect them.
 *
 * <pre>
 *   EMA Crossover (strategy)
 *   │
 *   ├── EMA CROSSOVER (indicator)
 *   │     ├── K
 *   │     └── D
 *   │
 *   ├── SL                 universal
 *   ├── TP                 universal
 *   ├── Quantity           universal
 *   ├── Candle Duration    universal
 *   └── Trigger Duration   universal
 * </pre>
 *
 * Every step keys on a unique business column - {@code parameters.code},
 * {@code indicators.name}, {@code strategies.name}, and the two link-table
 * unique constraints - so running this any number of times converges on the same
 * rows and never duplicates one.
 *
 * Nothing here is EMA-specific machinery: this class only supplies DATA to the
 * generic Strategy - Indicator - Parameter model. A second strategy is more rows
 * through the same three tables, authored here or through the API, with no code
 * change anywhere.
 */
@Component
public class ControlPlaneSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneSeeder.class);

    public static final String EMA_CROSSOVER = "EMA Crossover";
    public static final String EMA_CROSSOVER_INDICATOR = "EMA CROSSOVER";

    /**
     * Binds the strategy onto its indicator. {@code $k} and {@code $d} resolve
     * against the derived knob set, which the parameter catalog produces.
     */
    private static final String RULE_TREE = """
            {"entry":{"ind":"EMA CROSSOVER","params":{"k":"$k","d":"$d"}}}""";

    private static final String TIMEFRAME_OPTIONS =
            "{\"options\":[\"1m\",\"3m\",\"5m\",\"15m\",\"30m\",\"1h\",\"4h\",\"1d\"]}";

    private final ParameterRepository parameterRepository;
    private final IndicatorRepository indicatorRepository;
    private final IndicatorParameterLinkRepository indicatorParameterRepository;
    private final StrategyTemplateRepository strategyRepository;
    private final SharedStrategyConfigRepository sharedConfigRepository;
    private final StrategyIndicatorLinkSync indicatorSync;
    private final StrategyParameterLinkSync parameterSync;

    public ControlPlaneSeeder(ParameterRepository parameterRepository,
                              IndicatorRepository indicatorRepository,
                              IndicatorParameterLinkRepository indicatorParameterRepository,
                              StrategyTemplateRepository strategyRepository,
                              SharedStrategyConfigRepository sharedConfigRepository,
                              StrategyIndicatorLinkSync indicatorSync,
                              StrategyParameterLinkSync parameterSync) {
        this.parameterRepository = parameterRepository;
        this.indicatorRepository = indicatorRepository;
        this.indicatorParameterRepository = indicatorParameterRepository;
        this.strategyRepository = strategyRepository;
        this.sharedConfigRepository = sharedConfigRepository;
        this.indicatorSync = indicatorSync;
        this.parameterSync = parameterSync;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedParameterCatalog();
        seedIndicators();
        seedEmaCrossoverStrategy();

        // Derived layers, in order: the rule tree indexes into strategy_indicator_links,
        // and the parameter sync then walks those links to build the knob set.
        indicatorSync.syncAll();
        parameterSync.syncAll();
    }

    // ----------------------------------------------------- parameter catalog

    /**
     * The canonical parameters. Each exists once, whoever uses it.
     *
     * {@code universal} marks the ones every strategy gets automatically - the
     * execution settings that are not a property of any particular strategy.
     */
    private void seedParameterCatalog() {
        // Indicator parameters - signal scope, so they enter the config hash and
        // two users asking for the same values share one computation.
        seedParameter("k", "K", StrategyParamDefinition.TYPE_INT, Parameter.SCOPE_SIGNAL, "9",
                "{\"min\":1,\"max\":300}", "Fast leg length of the crossover", false, 1);
        seedParameter("d", "D", StrategyParamDefinition.TYPE_INT, Parameter.SCOPE_SIGNAL, "21",
                "{\"min\":1,\"max\":300,\"gt\":\"k\"}", "Slow leg length of the crossover", false, 2);
        // Shared by EMA and RSI at different ranges - see the per-link overrides.
        seedParameter("period", "Period", StrategyParamDefinition.TYPE_INT, Parameter.SCOPE_SIGNAL, "14",
                "{\"min\":2,\"max\":300}", "Lookback length", false, 3);

        // StrategyTemplate parameters - execution scope, personal, never hashed. The
        // display order is the order a form should render them in.
        seedParameter("sl", "SL", StrategyParamDefinition.TYPE_DECIMAL, Parameter.SCOPE_EXECUTION, "1.5",
                "{\"min\":0.1,\"max\":50}", "Stop loss percentage", true, 10);
        seedParameter("tp", "TP", StrategyParamDefinition.TYPE_DECIMAL, Parameter.SCOPE_EXECUTION, "3.0",
                "{\"min\":0.1,\"max\":100}", "Take profit percentage", true, 11);
        seedParameter("quantity", "Quantity", StrategyParamDefinition.TYPE_INT, Parameter.SCOPE_EXECUTION, "1",
                "{\"min\":1,\"max\":1000000}", "Order size", true, 12);
        seedParameter("candle_duration", "Candle Duration", StrategyParamDefinition.TYPE_TIMEFRAME,
                Parameter.SCOPE_EXECUTION, "5m", TIMEFRAME_OPTIONS,
                "Candle size the strategy evaluates on", true, 13);
        seedParameter("trigger_duration", "Trigger Duration", StrategyParamDefinition.TYPE_TIMEFRAME,
                Parameter.SCOPE_EXECUTION, "5m", TIMEFRAME_OPTIONS,
                "How often the entry condition is re-evaluated", true, 14);
    }

    private Parameter seedParameter(String code, String name, String dataType, String scope,
                                    String defaultValue, String validation, String description,
                                    boolean universal, int displayOrder) {
        return parameterRepository.findByCode(code).orElseGet(() -> {
            Parameter parameter = new Parameter();
            parameter.setCode(code);
            parameter.setName(name);
            parameter.setDataType(dataType);
            parameter.setScope(scope);
            parameter.setDefaultValue(defaultValue);
            parameter.setValidation(validation);
            parameter.setDescription(description);
            parameter.setUniversal(universal);
            parameter.setDisplayOrder(displayOrder);
            parameter.setSystem(true);
            Parameter saved = parameterRepository.save(parameter);
            log.info("Seeded parameter '{}' ({} scope{}) id={}",
                    code, scope, universal ? ", universal" : "", saved.getId());
            return saved;
        });
    }

    // ------------------------------------------------------------ indicators

    private void seedIndicators() {
        // The composite the predefined strategy uses: one indicator, two knobs.
        Indicator crossover = seedIndicator(EMA_CROSSOVER_INDICATOR,
                "{\"k\":{\"type\":\"int\",\"min\":1,\"max\":300},"
                        + "\"d\":{\"type\":\"int\",\"min\":1,\"max\":300}}");
        linkIndicatorParameterLink(crossover, "k", null, null, 1);
        linkIndicatorParameterLink(crossover, "d", null, null, 2);

        // Primitives, kept for the strategies that will use them. Both take
        // `period`, from one catalog row, narrowed per indicator - which is why
        // the link row carries the override columns.
        Indicator ema = seedIndicator("EMA", "{\"period\":{\"type\":\"int\",\"min\":2,\"max\":300}}");
        linkIndicatorParameterLink(ema, "period", "9", "{\"min\":2,\"max\":300}", 1);

        Indicator rsi = seedIndicator("RSI", "{\"period\":{\"type\":\"int\",\"min\":2,\"max\":100}}");
        linkIndicatorParameterLink(rsi, "period", "14", "{\"min\":2,\"max\":100}", 1);
    }

    private Indicator seedIndicator(String name, String paramSchema) {
        return indicatorRepository.findByName(name).orElseGet(() -> {
            Indicator def = new Indicator();
            def.setName(name);
            def.setParamSchema(paramSchema);
            def.setActive(true);
            Indicator saved = indicatorRepository.save(def);
            log.info("Seeded indicator '{}' id={}", name, saved.getId());
            return saved;
        });
    }

    /** Idempotent on UNIQUE (indicator_id, parameter_id). */
    private void linkIndicatorParameterLink(Indicator indicator, String parameterCode,
                                        String defaultOverride, String validationOverride, int order) {
        if (indicatorParameterRepository
                .findByIndicator_IdAndParameter_Code(indicator.getId(), parameterCode).isPresent()) {
            return;
        }
        Parameter parameter = parameterRepository.findByCode(parameterCode).orElseThrow(
                () -> new IllegalStateException("Parameter catalog is missing '" + parameterCode + "'"));

        IndicatorParameterLink link = new IndicatorParameterLink();
        link.setIndicator(indicator);
        link.setParameter(parameter);
        link.setDefaultValue(defaultOverride);
        link.setValidation(validationOverride);
        link.setDisplayOrder(order);
        link.setRequired(true);
        indicatorParameterRepository.save(link);
        log.info("Linked parameter '{}' to indicator '{}'", parameterCode, indicator.getName());
    }

    // ------------------------------------------------------------- strategy

    private void seedEmaCrossoverStrategy() {
        StrategyTemplate strategy = strategyRepository.findByName(EMA_CROSSOVER).orElse(null);
        if (strategy == null) {
            strategy = new StrategyTemplate();
            strategy.setName(EMA_CROSSOVER);
            strategy.setDescription("Long when the fast leg crosses above the slow leg; "
                    + "exit on the reverse cross or on SL/TP.");
            strategy.setRuleTree(RULE_TREE);
            strategy.setSystem(true);
            strategy.setActive(true);
            strategy = strategyRepository.save(strategy);
            log.info("Seeded strategy '{}' id={}", EMA_CROSSOVER, strategy.getId());
            return;
        }

        // An earlier build seeded this strategy against the EMA primitive with
        // $fast/$slow bindings. Converge it onto the catalog model - but not while
        // instances exist, because their signal params were hashed under the old
        // knob set and rewriting the tree would strand them.
        if (!RULE_TREE.equals(strategy.getRuleTree())) {
            long instances = sharedConfigRepository.countByStrategy_Id(strategy.getId());
            if (instances > 0) {
                log.warn("Strategy template '{}' still uses the pre-catalog rule tree and has {} instance(s); "
                        + "leaving it alone. Retire those instances to let it converge.",
                        EMA_CROSSOVER, instances);
                return;
            }
            strategy.setRuleTree(RULE_TREE);
            strategyRepository.save(strategy);
            log.info("Converged strategy '{}' onto the catalog rule tree", EMA_CROSSOVER);
        }
    }
}

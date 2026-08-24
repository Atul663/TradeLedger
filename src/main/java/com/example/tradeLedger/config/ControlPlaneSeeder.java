package com.example.tradeLedger.config;

import com.example.tradeLedger.entity.FixedParameter;
import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.StrategyTemplate;
import com.example.tradeLedger.repository.FixedParameterRepository;
import com.example.tradeLedger.repository.IndicatorRepository;
import com.example.tradeLedger.repository.StrategyTemplateRepository;
import com.example.tradeLedger.repository.UserStrategyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the platform catalog: the indicators, the templates whose rule trees use
 * them, and the descriptors of the fixed knobs a form renders.
 *
 * <pre>
 *   indicators                       strategy_templates
 *     EMA CROSSOVER  k, d              EMA Crossover   -> EMA CROSSOVER
 *     EMA AVERAGING  k, d              EMA Averaging   -> EMA AVERAGING
 *     EMA            period
 *     RSI            period
 *
 *   fixed_parameters                 one descriptor per fixed COLUMN
 *     candleDuration  timeframe        user_strategies.candle_duration
 *     lotRule         enum             user_strategies.lot_rule
 *     slPct           decimal          user_strategies.sl_pct
 * </pre>
 *
 * Two tables and one rule tree between the first two - there is no parameter
 * catalog to seed and no link table to reconcile. An indicator declares its own
 * knobs in {@code param_schema}, and everything else a strategy has is a typed
 * column on {@code user_strategies}, the same for every template.
 *
 * {@code fixed_parameters} does not change that: it holds no values, only what
 * each of those columns is called, what type it takes and what a form should
 * pre-fill.
 *
 * Every step keys on a unique business column ({@code indicators.name},
 * {@code strategy_templates.name}, {@code fixed_parameters.name}) so running this
 * any number of times converges on the same rows and never duplicates one.
 */
@Component
public class ControlPlaneSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneSeeder.class);

    public static final String EMA_CROSSOVER = "EMA Crossover";
    public static final String EMA_CROSSOVER_INDICATOR = "EMA CROSSOVER";

    public static final String EMA_AVERAGING = "EMA Averaging";
    public static final String EMA_AVERAGING_INDICATOR = "EMA AVERAGING";

    /**
     * The classic crossover: a fast leg and a slow one, so {@code d} must exceed
     * {@code k}.
     */
    private static final String EMA_CROSSOVER_SCHEMA = """
            {"k":{"type":"int","min":1,"max":300,"default":9},\
            "d":{"type":"int","min":1,"max":300,"default":21,"gt":"k"}}""";

    /**
     * The averaging variant: {@code k} is the EMA of the highs and {@code d} the
     * shorter signal leg, so the constraint runs the other way - 21/9 and 50/21
     * are both valid, 9/21 is not.
     */
    private static final String EMA_AVERAGING_SCHEMA = """
            {"k":{"type":"int","min":1,"max":300,"default":21},\
            "d":{"type":"int","min":1,"max":300,"default":9,"lt":"k"}}""";

    /** The sections a strategy form is laid out in, and the order they appear. */
    private static final String GROUP_MARKET = "market";
    private static final String GROUP_INSTRUMENT = "instrument";
    private static final String GROUP_SIZING = "sizing";
    private static final String GROUP_EXITS = "exits";
    private static final String GROUP_DEPLOYMENT = "deployment";

    /** Mirrors {@code ck_user_strategies_ce_strike} / {@code _pe_strike}. */
    private static final String STRIKE_OFFSET_BOUNDS = """
            {"min":0,"max":15}""";

    private static final String MONEYNESS_OPTIONS = """
            {"options":["ATM","ITM","OTM"]}""";

    /** {@code numeric(6,2)} percentages, neither of which is meaningful past 100. */
    private static final String PERCENT_BOUNDS = """
            {"min":0,"max":100}""";

    private static final String EMA_CROSSOVER_TREE = """
            {"entry":{"ind":"EMA CROSSOVER","params":{"k":"$k","d":"$d"}}}""";

    private static final String EMA_AVERAGING_TREE = """
            {"entry":{"ind":"EMA AVERAGING","params":{"k":"$k","d":"$d"}}}""";

    private final IndicatorRepository indicatorRepository;
    private final StrategyTemplateRepository templateRepository;
    private final UserStrategyRepository userStrategyRepository;
    private final FixedParameterRepository fixedParameterRepository;

    public ControlPlaneSeeder(IndicatorRepository indicatorRepository,
                              StrategyTemplateRepository templateRepository,
                              UserStrategyRepository userStrategyRepository,
                              FixedParameterRepository fixedParameterRepository) {
        this.indicatorRepository = indicatorRepository;
        this.templateRepository = templateRepository;
        this.userStrategyRepository = userStrategyRepository;
        this.fixedParameterRepository = fixedParameterRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIndicators();
        seedTemplates();
        seedFixedParameters();
    }

    // ------------------------------------------------------------ indicators

    private void seedIndicators() {
        seedIndicator(EMA_CROSSOVER_INDICATOR, EMA_CROSSOVER_SCHEMA);
        seedIndicator(EMA_AVERAGING_INDICATOR, EMA_AVERAGING_SCHEMA);

        // Primitives, for the templates that will use them singly.
        seedIndicator("EMA", """
                {"period":{"type":"int","min":2,"max":300,"default":9}}""");
        seedIndicator("RSI", """
                {"period":{"type":"int","min":2,"max":100,"default":14}}""");
    }

    /**
     * Idempotent on {@code indicators.name}, and it CONVERGES the schema.
     *
     * The schema is the only declaration of what an indicator takes and what
     * applies by default, so a platform retune has to be able to land here. It is
     * safe to overwrite because user values live in their own rows: a key that
     * disappears is rejected on the next write of a strategy that still sets it,
     * rather than silently changing what anyone runs today.
     */
    private void seedIndicator(String name, String paramSchema) {
        Indicator indicator = indicatorRepository.findByName(name).orElse(null);
        if (indicator == null) {
            indicator = new Indicator();
            indicator.setName(name);
            indicator.setParamSchema(paramSchema);
            indicator.setActive(true);
            log.info("Seeded indicator {} id={}", name, indicatorRepository.save(indicator).getId());
            return;
        }
        if (!paramSchema.equals(indicator.getParamSchema())) {
            indicator.setParamSchema(paramSchema);
            indicatorRepository.save(indicator);
            log.info("Converged indicator {} onto the current parameter schema", name);
        }
    }

    // ------------------------------------------------------------- templates

    private void seedTemplates() {
        seedTemplate(EMA_CROSSOVER, EMA_CROSSOVER_TREE,
                "Long when the fast leg crosses above the slow leg; exit on the reverse cross or on SL/TP.");
        seedTemplate(EMA_AVERAGING, EMA_AVERAGING_TREE,
                "EMA of the highs against a shorter signal leg, traded through options or the future, "
                        + "with a configurable averaging ladder.");
    }

    private void seedTemplate(String name, String ruleTree, String description) {
        StrategyTemplate template = templateRepository.findByName(name).orElse(null);
        if (template == null) {
            template = new StrategyTemplate();
            template.setName(name);
            template.setDescription(description);
            template.setRuleTree(ruleTree);
            template.setSystem(true);
            template.setActive(true);
            log.info("Seeded template {} id={}", name, templateRepository.save(template).getId());
            return;
        }

        // Rewriting the tree decides which indicator rows a strategy carries, so it
        // is only safe while nobody has built one. With strategies in place their
        // indicator rows and hashed values were settled under the old tree, and
        // moving it would strand them.
        if (!ruleTree.equals(template.getRuleTree())) {
            long strategies = userStrategyRepository.countByStrategy_Id(template.getId());
            if (strategies > 0) {
                log.warn("Template {} still uses an older rule tree and is used by {} strategy(ies); "
                        + "leaving it alone.", name, strategies);
                return;
            }
            template.setRuleTree(ruleTree);
            templateRepository.save(template);
            log.info("Converged template {} onto the current rule tree", name);
        }
    }

    // ------------------------------------------------------ fixed parameters

    /**
     * One descriptor per fixed COLUMN, in the order a form lays them out.
     *
     * The columns themselves are the source of truth for what a strategy runs
     * with - these rows only say what the field is called, what it takes and what
     * to pre-fill. Where a knob is bounded by a CHECK constraint, the validation
     * here mirrors it, so a form refuses what the database would have refused.
     */
    private void seedFixedParameters() {
        seedFixedParameter(GROUP_MARKET, 1, "candleDuration", "Time frame",
                FixedParameter.TYPE_TIMEFRAME, FixedParameter.SCOPE_SIGNAL, "5m", null, true,
                "The candle the strategy evaluates on. Part of the shared config's identity, "
                        + "so two users on different candles cannot share one computation.");
        seedFixedParameter(GROUP_MARKET, 2, "triggerDuration", "Trigger interval",
                FixedParameter.TYPE_TIMEFRAME, FixedParameter.SCOPE_EXECUTION, "1m", null, false,
                "How often the entry condition is re-checked inside a candle. It changes when "
                        + "you look, not what is computed.");

        seedFixedParameter(GROUP_INSTRUMENT, 1, "derivative", "Derivative",
                FixedParameter.TYPE_ENUM, FixedParameter.SCOPE_EXECUTION, "OPTION",
                """
                        {"options":["FUT","OPTION"]}""", true,
                "Whether the signal is traded through the future or through options. The CE and "
                        + "PE sides apply only to OPTION.");
        seedFixedParameter(GROUP_INSTRUMENT, 2, "ceEnabled", "Trade the call side",
                FixedParameter.TYPE_BOOL, FixedParameter.SCOPE_EXECUTION, "false", null, false,
                "Both sides may run at once.");
        seedFixedParameter(GROUP_INSTRUMENT, 3, "ceMoneyness", "Call moneyness",
                FixedParameter.TYPE_ENUM, FixedParameter.SCOPE_EXECUTION, "ATM",
                MONEYNESS_OPTIONS, false,
                "Where the call strike sits relative to spot.");
        seedFixedParameter(GROUP_INSTRUMENT, 4, "ceStrikeOffset", "Call strike depth",
                FixedParameter.TYPE_INT, FixedParameter.SCOPE_EXECUTION, "0",
                STRIKE_OFFSET_BOUNDS, false,
                "0 for ATM, 1..15 for ITM and OTM - 'OTM3' is 3.");
        seedFixedParameter(GROUP_INSTRUMENT, 5, "peEnabled", "Trade the put side",
                FixedParameter.TYPE_BOOL, FixedParameter.SCOPE_EXECUTION, "false", null, false,
                "Chosen independently of the call side.");
        seedFixedParameter(GROUP_INSTRUMENT, 6, "peMoneyness", "Put moneyness",
                FixedParameter.TYPE_ENUM, FixedParameter.SCOPE_EXECUTION, "ATM",
                MONEYNESS_OPTIONS, false,
                "Where the put strike sits relative to spot.");
        seedFixedParameter(GROUP_INSTRUMENT, 7, "peStrikeOffset", "Put strike depth",
                FixedParameter.TYPE_INT, FixedParameter.SCOPE_EXECUTION, "0",
                STRIKE_OFFSET_BOUNDS, false,
                "0 for ATM, 1..15 for ITM and OTM.");

        seedFixedParameter(GROUP_SIZING, 1, "lotRule", "Averaging rule",
                FixedParameter.TYPE_ENUM, FixedParameter.SCOPE_EXECUTION, "FIXED",
                """
                        {"options":["FIXED","DOUBLE","CUMULATIVE"]}""", true,
                "How each averaging entry is sized. From a base lot of 65: FIXED gives "
                        + "65/65/65, DOUBLE gives 65/130/260, CUMULATIVE gives 65/130/195.");
        seedFixedParameter(GROUP_SIZING, 2, "baseLot", "Base lot",
                FixedParameter.TYPE_INT, FixedParameter.SCOPE_EXECUTION, "1",
                """
                        {"min":1}""", true,
                "The first entry's size, in contracts.");
        seedFixedParameter(GROUP_SIZING, 3, "averagingCount", "Averaging count",
                FixedParameter.TYPE_INT, FixedParameter.SCOPE_EXECUTION, "0",
                """
                        {"min":0,"max":10}""", false,
                "How many times the strategy may add to a losing position. 0 means never.");

        seedFixedParameter(GROUP_EXITS, 1, "slPct", "Stop loss %",
                FixedParameter.TYPE_DECIMAL, FixedParameter.SCOPE_EXECUTION, null,
                PERCENT_BOUNDS, false,
                "Percent move against the position that closes it. Empty means the strategy "
                        + "carries no stop of its own.");
        seedFixedParameter(GROUP_EXITS, 2, "tpPct", "Take profit %",
                FixedParameter.TYPE_DECIMAL, FixedParameter.SCOPE_EXECUTION, null,
                PERCENT_BOUNDS, false,
                "Percent move in favour of the position that closes it.");

        seedFixedParameter(GROUP_DEPLOYMENT, 1, "executionMode", "Execution mode",
                FixedParameter.TYPE_ENUM, FixedParameter.SCOPE_EXECUTION, "FIXED_QTY",
                """
                        {"options":["FIXED_QTY","CAPITAL_PERCENT","RISK_PERCENT"]}""", true,
                "How a deployment turns the strategy's sizing into an order quantity.");
        seedFixedParameter(GROUP_DEPLOYMENT, 2, "multiplier", "Size multiplier",
                FixedParameter.TYPE_DECIMAL, FixedParameter.SCOPE_EXECUTION, "1",
                """
                        {"min":0}""", false,
                "Scales this deployment's size against the strategy's, so one account can run "
                        + "the same strategy larger than another.");
        seedFixedParameter(GROUP_DEPLOYMENT, 3, "capitalAllocated", "Capital allocated",
                FixedParameter.TYPE_DECIMAL, FixedParameter.SCOPE_EXECUTION, null,
                """
                        {"min":0}""", false,
                "What the percent execution modes are a percent OF.");
        seedFixedParameter(GROUP_DEPLOYMENT, 4, "tradeMode", "Trade mode",
                FixedParameter.TYPE_ENUM, FixedParameter.SCOPE_EXECUTION, "paper",
                """
                        {"options":["paper","live"]}""", true,
                "paper places nothing with the broker. The default, deliberately.");
    }

    /**
     * Idempotent on {@code fixed_parameters.name}, and INSERT-ONLY.
     *
     * Unlike an indicator's schema, nothing here is read to decide what anyone
     * runs, and {@code /api/v1/fixed-parameters} is the intended way to retune a
     * label or a suggested default. Converging on boot would silently undo an
     * admin's edit on the next deploy, so an existing row is left exactly as it
     * is - a knob that has genuinely changed shape is a PUT, not a redeploy.
     */
    private void seedFixedParameter(String group, int order, String name, String label,
                                    String dataType, String scope, String defaultValue,
                                    String validation, boolean required, String description) {
        if (fixedParameterRepository.existsByNameIgnoreCase(name)) {
            return;
        }
        FixedParameter parameter = new FixedParameter();
        parameter.setName(name);
        parameter.setLabel(label);
        parameter.setDescription(description);
        parameter.setDataType(dataType);
        parameter.setScope(scope);
        parameter.setDefaultValue(defaultValue);
        parameter.setValidation(validation);
        parameter.setParamGroup(group);
        parameter.setDisplayOrder(order);
        parameter.setRequired(required);
        parameter.setActive(true);
        log.info("Seeded fixed parameter {} ({}) id={}",
                name, dataType, fixedParameterRepository.save(parameter).getId());
    }
}

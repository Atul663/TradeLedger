package com.example.tradeLedger.config;

import com.example.tradeLedger.entity.IndicatorDef;
import com.example.tradeLedger.entity.Strategy;
import com.example.tradeLedger.entity.StrategyParamDef;
import com.example.tradeLedger.repository.IndicatorDefRepository;
import com.example.tradeLedger.repository.StrategyParamDefRepository;
import com.example.tradeLedger.repository.StrategyRepository;
import com.example.tradeLedger.serviceImpl.StrategyIndicatorSync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the catalog rows the design ships as its worked example: the EMA and RSI
 * indicator primitives, the EMA Crossover strategy, and that strategy's four
 * knobs.
 *
 * Mirrors the SEED section of {@code db/control-plane-schema.sql} exactly. Both
 * are idempotent and both key on the unique business columns, so running either
 * or both - in any order - converges on the same rows.
 *
 * A second strategy needs no Java: insert another {@code strategies} row with its
 * rule tree plus its {@code strategy_param_defs}, and the whole
 * configuration / validation / dedup path picks it up.
 */
@Component
public class ControlPlaneSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneSeeder.class);

    public static final String EMA_CROSSOVER = "EMA Crossover";

    private static final String RULE_TREE = """
            {"entry":{"cross_above":[{"ind":"EMA","params":{"period":"$fast"}},
                                     {"ind":"EMA","params":{"period":"$slow"}}]},
             "exit":{"cross_below":[{"ind":"EMA","params":{"period":"$fast"}},
                                    {"ind":"EMA","params":{"period":"$slow"}}]}}""";

    private final IndicatorDefRepository indicatorDefRepository;
    private final StrategyRepository strategyRepository;
    private final StrategyParamDefRepository paramDefRepository;
    private final StrategyIndicatorSync indicatorSync;

    public ControlPlaneSeeder(IndicatorDefRepository indicatorDefRepository,
                              StrategyRepository strategyRepository,
                              StrategyParamDefRepository paramDefRepository,
                              StrategyIndicatorSync indicatorSync) {
        this.indicatorDefRepository = indicatorDefRepository;
        this.strategyRepository = strategyRepository;
        this.paramDefRepository = paramDefRepository;
        this.indicatorSync = indicatorSync;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIndicator("EMA", "{\"period\":{\"type\":\"int\",\"min\":2,\"max\":300}}");
        seedIndicator("RSI", "{\"period\":{\"type\":\"int\",\"min\":2,\"max\":100}}");

        Strategy strategy = strategyRepository.findByName(EMA_CROSSOVER).orElse(null);
        if (strategy == null) {
            strategy = new Strategy();
            strategy.setName(EMA_CROSSOVER);
            strategy.setDescription("Long when fast EMA crosses above slow EMA; "
                    + "exit on reverse cross or SL/TP.");
            strategy.setRuleTree(RULE_TREE);
            strategy.setSystem(true);
            strategy.setActive(true);
            strategy = strategyRepository.save(strategy);
            log.info("Seeded strategy '{}' id={}", EMA_CROSSOVER, strategy.getId());
        }

        // Signal scope: hashed, shared between every user running the same math.
        seedParam(strategy, "fast", StrategyParamDef.TYPE_INT, StrategyParamDef.SCOPE_SIGNAL, "9",
                "{\"min\":2,\"max\":200}", "Fast EMA period", 1);
        seedParam(strategy, "slow", StrategyParamDef.TYPE_INT, StrategyParamDef.SCOPE_SIGNAL, "21",
                "{\"min\":3,\"max\":300,\"gt\":\"fast\"}", "Slow EMA period", 2);

        // Execution scope: personal, never hashed - this is what lets two users
        // share EMA(9)/EMA(21) while exiting at different stop losses.
        seedParam(strategy, "sl_pct", StrategyParamDef.TYPE_DECIMAL, StrategyParamDef.SCOPE_EXECUTION, "1.5",
                "{\"min\":0.1,\"max\":20}", "Stop loss %", 3);
        seedParam(strategy, "tp_pct", StrategyParamDef.TYPE_DECIMAL, StrategyParamDef.SCOPE_EXECUTION, "3.0",
                "{\"min\":0.1,\"max\":50}", "Take profit %", 4);

        // The strategy-indicator index is derived from rule trees, and rows
        // seeded here never went through the API that maintains it. Rebuilding
        // once at startup also covers strategies inserted straight into the
        // database, and is idempotent like everything else in this class.
        indicatorSync.syncAll();
    }

    private void seedIndicator(String name, String paramSchema) {
        if (indicatorDefRepository.existsByName(name)) {
            return;
        }
        IndicatorDef def = new IndicatorDef();
        def.setName(name);
        def.setParamSchema(paramSchema);
        def.setActive(true);
        indicatorDefRepository.save(def);
        log.info("Seeded indicator '{}'", name);
    }

    private void seedParam(Strategy strategy, String key, String dataType, String scope,
                           String defaultValue, String validation, String label, int order) {
        if (paramDefRepository.existsByStrategy_IdAndParameterKey(strategy.getId(), key)) {
            return;
        }
        StrategyParamDef def = new StrategyParamDef();
        def.setStrategy(strategy);
        def.setParameterKey(key);
        def.setDataType(dataType);
        def.setScope(scope);
        def.setDefaultValue(defaultValue);
        def.setValidation(validation);
        def.setDisplayLabel(label);
        def.setDisplayOrder(order);
        def.setRequired(true);
        paramDefRepository.save(def);
        log.info("Seeded param '{}.{}' ({} scope)", EMA_CROSSOVER, key, scope);
    }
}

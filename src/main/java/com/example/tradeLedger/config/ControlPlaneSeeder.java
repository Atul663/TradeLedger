package com.example.tradeLedger.config;

import com.example.tradeLedger.entity.Indicator;
import com.example.tradeLedger.entity.StrategyTemplate;
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
 * Seeds the platform catalog: the indicators, and the templates whose rule trees
 * use them.
 *
 * <pre>
 *   indicators                       strategy_templates
 *     EMA CROSSOVER  k, d              EMA Crossover   -> EMA CROSSOVER
 *     EMA AVERAGING  k, d              EMA Averaging   -> EMA AVERAGING
 *     EMA            period
 *     RSI            period
 * </pre>
 *
 * Two tables and one rule tree between them - there is no parameter catalog to
 * seed and no link table to reconcile. An indicator declares its own knobs in
 * {@code param_schema}, and everything else a strategy has is a typed column on
 * {@code user_strategies}, the same for every template.
 *
 * Every step keys on a unique business column ({@code indicators.name},
 * {@code strategy_templates.name}) so running this any number of times converges
 * on the same rows and never duplicates one.
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

    private static final String EMA_CROSSOVER_TREE = """
            {"entry":{"ind":"EMA CROSSOVER","params":{"k":"$k","d":"$d"}}}""";

    private static final String EMA_AVERAGING_TREE = """
            {"entry":{"ind":"EMA AVERAGING","params":{"k":"$k","d":"$d"}}}""";

    private final IndicatorRepository indicatorRepository;
    private final StrategyTemplateRepository templateRepository;
    private final UserStrategyRepository userStrategyRepository;

    public ControlPlaneSeeder(IndicatorRepository indicatorRepository,
                              StrategyTemplateRepository templateRepository,
                              UserStrategyRepository userStrategyRepository) {
        this.indicatorRepository = indicatorRepository;
        this.templateRepository = templateRepository;
        this.userStrategyRepository = userStrategyRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIndicators();
        seedTemplates();
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
}

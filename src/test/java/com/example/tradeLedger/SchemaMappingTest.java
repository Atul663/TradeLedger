package com.example.tradeLedger;

import com.example.tradeLedger.entity.*;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.CheckConstraint;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds the Hibernate mapping model against the Postgres dialect, with no
 * database, and asserts on the schema it would create.
 *
 * The application runs on {@code ddl-auto=update}, which reports a mapping it
 * cannot satisfy as a WARN in the startup log rather than a failure - so a column
 * that never lands or a constraint that is silently dropped stays invisible until
 * something reads the table. This turns that into a test.
 *
 * What it pins is the shape the design depends on: the instrument choice living
 * in columns, the CHECK constraints that make an impossible strike impossible,
 * indicator values as the one jsonb, and the absence of the key/value tables this
 * model replaced.
 */
class SchemaMappingTest {

    /**
     * Listed rather than scanned, so renaming or dropping an entity breaks the
     * compile here instead of silently shrinking the schema under test.
     */
    private static final List<Class<?>> ENTITIES = List.of(
            User.class, Exchange.class, Symbol.class, Broker.class, UserBroker.class,
            TradingAccount.class, BrokerCredential.class, BrokerCredentialField.class,
            RiskProfile.class, UserRiskLimit.class,
            Indicator.class, FixedParameter.class, StrategyTemplate.class, SharedStrategyConfig.class,
            UserStrategy.class, UserStrategyIndicator.class, StrategySubscription.class,
            GoogleAuthToken.class, PlatformStrategyToggle.class);

    private static Metadata metadata() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .build();
        MetadataSources sources = new MetadataSources(registry);
        ENTITIES.forEach(sources::addAnnotatedClass);
        return sources.buildMetadata();
    }

    private static Table table(Metadata metadata, Class<?> entity) {
        PersistentClass binding = metadata.getEntityBinding(entity.getName());
        assertTrue(binding != null, entity.getSimpleName() + " is not a mapped entity");
        return binding.getTable();
    }

    private static Set<String> columns(Table table) {
        Set<String> names = new TreeSet<>();
        for (Column column : table.getColumns()) {
            names.add(column.getName().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static Set<String> tableNames(Metadata metadata) {
        Set<String> names = new TreeSet<>();
        for (Table table : metadata.collectTableMappings()) {
            names.add(table.getName().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    @Test
    void everyListedEntityIsMapped() {
        Metadata metadata = metadata();
        assertEquals("user_strategies", table(metadata, UserStrategy.class).getName());
        assertEquals("user_strategy_indicators", table(metadata, UserStrategyIndicator.class).getName());
        assertEquals("user_strategy_subscriptions", table(metadata, StrategySubscription.class).getName());
    }

    /** The instrument choice is columns, not rows - that is the whole point. */
    @Test
    void theInstrumentChoiceIsColumnsOnTheStrategy() {
        Set<String> columns = columns(table(metadata(), UserStrategy.class));

        for (String column : List.of("derivative",
                "ce_enabled", "ce_moneyness", "ce_strike_offset",
                "pe_enabled", "pe_moneyness", "pe_strike_offset",
                "lot_rule", "base_lot", "averaging_count",
                "candle_duration", "trigger_duration", "sl_pct", "tp_pct")) {
            assertTrue(columns.contains(column), "user_strategies is missing " + column + ": " + columns);
        }
    }

    /** Every edge out of a strategy is a foreign key, never a name string. */
    @Test
    void everyEdgeOutOfAStrategyIsAForeignKey() {
        Set<String> columns = columns(table(metadata(), UserStrategy.class));

        for (String fk : List.of("user_id", "strategy_id", "symbol_id", "shared_config_id")) {
            assertTrue(columns.contains(fk), "user_strategies is missing " + fk);
        }
    }

    /** A strike depth that disagrees with its moneyness has to be impossible, not merely refused. */
    @Test
    void strikeDepthAndSizingAreConstrainedByTheDatabase() {
        List<CheckConstraint> checks = table(metadata(), UserStrategy.class).getChecks();
        Set<String> names = new TreeSet<>();
        StringBuilder all = new StringBuilder();
        for (CheckConstraint check : checks) {
            names.add(check.getName());
            all.append(check.getConstraint().toLowerCase(Locale.ROOT)).append(' ');
        }

        assertTrue(names.contains("ck_user_strategies_ce_strike"), names.toString());
        assertTrue(names.contains("ck_user_strategies_pe_strike"), names.toString());
        assertTrue(names.contains("ck_user_strategies_sizing"), names.toString());
        assertTrue(all.toString().contains("between 1 and 15"), all.toString());
    }

    /** Indicator values are the one schemaless thing left, and they sit on their own row. */
    @Test
    void indicatorValuesAreJsonbOnTheirOwnRow() {
        Table table = table(metadata(), UserStrategyIndicator.class);
        Set<String> columns = columns(table);

        assertTrue(columns.contains("params"), columns.toString());
        assertTrue(columns.contains("user_strategy_id"), columns.toString());
        assertTrue(columns.contains("indicator_id"), columns.toString());
    }

    /**
     * A deployment points at its strategy and carries only what differs per
     * account. A copy of the configuration here is what would let a running broker
     * drift from the strategy it claims to run.
     */
    @Test
    void aDeploymentReachesItsConfigurationByForeignKey() {
        Table table = table(metadata(), StrategySubscription.class);
        Set<String> columns = columns(table);

        assertTrue(columns.contains("user_strategy_id"), columns.toString());
        assertTrue(table.getUniqueKeys().containsKey("uq_user_strategy_subs_strategy_account"),
                table.getUniqueKeys().keySet().toString());

        for (String copied : List.of("exec_params", "option_legs", "signal_params",
                "shared_config_id", "quantity", "lot_size")) {
            assertFalse(columns.contains(copied),
                    "a deployment must not hold its own " + copied);
        }
    }

    /** The key/value layer this model replaced must not come back. */
    @Test
    void theParameterCatalogAndItsMappingTablesAreGone() {
        Set<String> tables = tableNames(metadata());

        for (String dropped : List.of("parameters", "indicator_parameter_links",
                "strategy_parameter_links", "strategy_indicator_links",
                "strategy_param_definitions", "user_strategy_parameters", "user_strategy_legs")) {
            assertFalse(tables.contains(dropped), dropped + " is back in the schema");
        }
    }

    /**
     * fixed_parameters describes the fixed knobs; it must never come to HOLD one.
     *
     * The line between this table and the catalog above is that no user row hangs
     * off it and no value is resolved through it. A user or strategy foreign key
     * here, or a custom_value column, would be that catalog again under a new
     * name - so the absence is asserted rather than left to review.
     */
    @Test
    void fixedParametersDescribesKnobsWithoutHoldingAnyValue() {
        Table table = table(metadata(), FixedParameter.class);
        Set<String> columns = columns(table);

        assertEquals("fixed_parameters", table.getName());
        for (String descriptor : List.of("name", "label", "data_type", "default_value",
                "validation", "param_group", "display_order")) {
            assertTrue(columns.contains(descriptor),
                    "fixed_parameters is missing " + descriptor + ": " + columns);
        }
        for (String value : List.of("user_id", "user_strategy_id", "strategy_id",
                "indicator_id", "custom_value", "parameter_id")) {
            assertFalse(columns.contains(value),
                    "fixed_parameters holds " + value + " - it is a descriptor catalog, "
                            + "not a value store");
        }
    }

    /**
     * broker_credential_fields describes the credential form; it must never come
     * to HOLD a credential.
     *
     * The same line fixed_parameters walks, and easier to cross here because the
     * thing being described IS a secret. A user or account foreign key, or an
     * api_key column, would make this a second copy of broker_credentials with no
     * encryption behind it - so the absence is asserted rather than left to
     * review.
     */
    @Test
    void brokerCredentialFieldsDescribesTheFormWithoutHoldingACredential() {
        Table table = table(metadata(), BrokerCredentialField.class);
        Set<String> columns = columns(table);

        assertEquals("broker_credential_fields", table.getName());
        for (String descriptor : List.of("broker_id", "field_key", "label", "description",
                "placeholder", "data_type", "default_value", "validation", "field_group",
                "display_order", "is_required", "is_user_supplied")) {
            assertTrue(columns.contains(descriptor),
                    "broker_credential_fields is missing " + descriptor + ": " + columns);
        }
        for (String value : List.of("user_id", "user_broker_id", "trading_account_id",
                "api_key", "api_secret", "access_token", "totp_secret", "custom_value")) {
            assertFalse(columns.contains(value),
                    "broker_credential_fields holds " + value + " - it is a descriptor "
                            + "catalog, not a credential store");
        }
    }
}

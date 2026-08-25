package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.FixedParameterGroupResponse;
import com.example.tradeLedger.dto.FixedParameterResponse;
import com.example.tradeLedger.dto.StrategyFixedParameterGroupResponse;
import com.example.tradeLedger.dto.StrategyFixedParameterResponse;
import com.example.tradeLedger.entity.FixedParameter;
import com.example.tradeLedger.entity.UserStrategy;
import com.example.tradeLedger.repository.FixedParameterRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arranges the FIXED knobs of a strategy the way a form lays them out: the
 * descriptor catalog joined to the strategy's own columns, grouped by section.
 *
 * <b>The join is by name, and that is the whole trick.</b> A
 * {@code fixed_parameters} row's {@code name} is by convention the API field name
 * of the column it describes - 'slPct' describes {@code user_strategies.sl_pct},
 * which the API already exposes as {@code slPct}. {@link #VALUE_READERS} is the
 * one place that pairing is written down, and it is what turns two independent
 * tables into a renderable form without either of them knowing about the other.
 *
 * <b>It adds no source of truth.</b> Every value here is the same one the flat
 * field on the response already carries, read from the same getter; writes still
 * address the flat name. Emptying {@code fixed_parameters} costs the grouped
 * arrangement and nothing else - which is the property the catalog was built to
 * have.
 *
 * The 'Deployment' group is deliberately absent: those descriptors name columns
 * on {@code user_strategy_subscriptions}, so they belong to a deployment, not to
 * the strategy it deploys.
 */
@Component
public class StrategyFixedParameters {

    /**
     * Descriptor name to the column it describes, for the knobs a
     * {@code user_strategies} row carries.
     *
     * A descriptor whose name is not a key here is simply not a strategy knob -
     * that is how the deployment-scope ones are excluded without hardcoding a
     * group name, and how a knob an admin invents through the API stays out of a
     * shape that has no column to fill it from.
     */
    private static final Map<String, ValueReader> VALUE_READERS = valueReaders();

    private final FixedParameterRepository repository;
    private final FixedParameterOptions options;

    public StrategyFixedParameters(FixedParameterRepository repository,
                                   FixedParameterOptions options) {
        this.repository = repository;
        this.options = options;
    }

    /** The names this class knows how to read off a strategy. */
    public static Set<String> knobNames() {
        return VALUE_READERS.keySet();
    }

    /**
     * One saved strategy's fixed knobs, with values, grouped by section.
     *
     * Ordered the way the catalog is - by group, then position within it, then
     * name - so the sections come back in the order a form renders them.
     *
     * @return an empty list if the catalog has been emptied; never null
     */
    public List<StrategyFixedParameterGroupResponse> forStrategy(UserStrategy strategy) {
        return snapshot().forStrategy(strategy);
    }

    /**
     * One reusable read of the catalog, for a response that renders the same form
     * for many strategies.
     *
     * The descriptors and their options are IDENTICAL for every strategy in a
     * list - only the values differ - so reading them per row is one round trip
     * per row for an answer that does not vary. A snapshot reads the catalog once,
     * carries one {@link FixedParameterOptions.Snapshot} with it, and answers the
     * rest from memory.
     *
     * Hold one for the length of a request and no longer.
     */
    public Snapshot snapshot() {
        return new Snapshot();
    }

    public final class Snapshot {

        private final Map<String, List<FixedParameter>> byGroup;
        private final FixedParameterOptions.Snapshot optionsSnapshot;

        private Snapshot() {
            this.byGroup = groupedRows();
            this.optionsSnapshot = options.snapshot();
        }

        /** One saved strategy's fixed knobs, with values, grouped by section. */
        public List<StrategyFixedParameterGroupResponse> forStrategy(UserStrategy strategy) {
            List<StrategyFixedParameterGroupResponse> groups = new ArrayList<>();
            byGroup.forEach((group, rows) -> {
                List<StrategyFixedParameterResponse> parameters = rows.stream()
                        .map(row -> new StrategyFixedParameterResponse(
                                row.getId(),
                                row.getName(),
                                row.getLabel(),
                                row.getDescription(),
                                row.getDataType(),
                                row.getScope(),
                                VALUE_READERS.get(row.getName()).read(strategy),
                                row.getDefaultValue(),
                                optionsSnapshot.validation(row),
                                row.getDisplayOrder(),
                                row.isRequired()))
                        .toList();
                groups.add(new StrategyFixedParameterGroupResponse(
                        group, parameters.size(), parameters));
            });
            return groups;
        }

        /** The same sections with no values in them - what a template can say. */
        public List<FixedParameterGroupResponse> descriptors() {
            List<FixedParameterGroupResponse> groups = new ArrayList<>();
            byGroup.forEach((group, rows) -> {
                List<FixedParameterResponse> parameters = rows.stream()
                        .map(row -> toResponse(row, optionsSnapshot))
                        .toList();
                groups.add(new FixedParameterGroupResponse(group, parameters.size(), parameters));
            });
            return groups;
        }
    }

    /**
     * The same sections with no values in them - what a template can say, having
     * no strategy behind it.
     *
     * A builder form drawing a blank template reads these; the same form reopened
     * on a saved strategy reads {@link #forStrategy}, and the two agree on which
     * fields exist and in what order because they walk the same catalog rows.
     */
    public List<FixedParameterGroupResponse> descriptors() {
        return snapshot().descriptors();
    }

    /**
     * The ACTIVE strategy-scope descriptors, in catalog order, collected by group.
     *
     * The repository already orders by group, so a LinkedHashMap collects the runs
     * and preserves the order the catalog put them in. Retired descriptors are
     * skipped - deactivating one is the non-destructive way to take a field off a
     * form, and it has to take it off this one too.
     */
    private Map<String, List<FixedParameter>> groupedRows() {
        Map<String, List<FixedParameter>> byGroup = new LinkedHashMap<>();
        for (FixedParameter row
                : repository.findByActiveOrderByParamGroupAscDisplayOrderAscNameAsc(true)) {
            if (VALUE_READERS.containsKey(row.getName())) {
                byGroup.computeIfAbsent(row.getParamGroup(), key -> new ArrayList<>()).add(row);
            }
        }
        return byGroup;
    }

    private FixedParameterResponse toResponse(FixedParameter row,
                                              FixedParameterOptions.Snapshot optionsSnapshot) {
        return new FixedParameterResponse(
                row.getId(),
                row.getName(),
                row.getLabel(),
                row.getDescription(),
                row.getDataType(),
                row.getScope(),
                row.getDefaultValue(),
                optionsSnapshot.validation(row),
                row.getParamGroup(),
                row.getDisplayOrder(),
                row.isRequired(),
                row.isActive(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    /**
     * Values come back already typed - a number stays a number, an enum comes back
     * as its name - so a client does not coerce a string it was handed as a
     * string. This is the same value the flat field carries, off the same getter.
     */
    private static Map<String, ValueReader> valueReaders() {
        Map<String, ValueReader> readers = new LinkedHashMap<>();
        // The ticker, not the id: it is what the knob's options offer and what a
        // PUT carries. Null until a market is chosen, which is the state
        // 'deployable: false' describes.
        readers.put("symbol", s -> s.getSymbol() != null ? s.getSymbol().getSymbol() : null);
        // Derived, not stored: a strategy has no exchange column, it has a symbol
        // and the symbol has a venue. Same value the flat exchangeCode field
        // carries, off the same path.
        readers.put("exchangeCode", s -> s.getSymbol() != null
                ? s.getSymbol().getExchange().getCode() : null);
        readers.put("candleDuration", UserStrategy::getCandleDuration);
        readers.put("triggerDuration", UserStrategy::getTriggerDuration);
        readers.put("derivative", s -> s.getDerivative() != null ? s.getDerivative().name() : null);
        readers.put("ceEnabled", UserStrategy::isCeEnabled);
        readers.put("ceMoneyness", s -> s.getCeMoneyness() != null ? s.getCeMoneyness().name() : null);
        readers.put("ceStrikeOffset", UserStrategy::getCeStrikeOffset);
        readers.put("peEnabled", UserStrategy::isPeEnabled);
        readers.put("peMoneyness", s -> s.getPeMoneyness() != null ? s.getPeMoneyness().name() : null);
        readers.put("peStrikeOffset", UserStrategy::getPeStrikeOffset);
        readers.put("lotRule", s -> s.getLotRule() != null ? s.getLotRule().name() : null);
        readers.put("baseLot", UserStrategy::getBaseLot);
        readers.put("averagingCount", UserStrategy::getAveragingCount);
        readers.put("slPct", UserStrategy::getSlPct);
        readers.put("tpPct", UserStrategy::getTpPct);
        return readers;
    }

    @FunctionalInterface
    private interface ValueReader {
        Object read(UserStrategy strategy);
    }
}

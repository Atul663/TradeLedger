package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.FixedParameterGroupResponse;
import com.example.tradeLedger.dto.FixedParameterResponse;
import com.example.tradeLedger.entity.FixedParameter;
import com.example.tradeLedger.repository.FixedParameterRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The FIXED knobs a strategy can carry, arranged the way a form lays them out:
 * the descriptor catalog, filtered to the strategy-scope knobs and grouped by
 * section.
 *
 * <b>The pairing is by name.</b> A {@code fixed_parameters} row's {@code name} is
 * by convention the API field name of the column it describes - 'slPct'
 * describes {@code user_strategies.sl_pct}, which the API already exposes as
 * {@code slPct}. {@link #KNOB_NAMES} is the one place that pairing is written
 * down; a form binds descriptor to field by string equality, and a PUT addresses
 * the flat name.
 *
 * <b>It adds no source of truth.</b> Values are not served from here - the
 * strategy response carries each one as a flat field. Emptying
 * {@code fixed_parameters} costs the descriptors a blank builder form renders
 * from and nothing else, which is the property the catalog was built to have.
 *
 * The 'Deployment' group is deliberately absent: those descriptors name columns
 * on {@code user_strategy_subscriptions}, so they belong to a deployment, not to
 * the strategy it deploys.
 */
@Component
public class StrategyFixedParameters {

    /**
     * The descriptor names that name a column a {@code user_strategies} row
     * carries.
     *
     * A descriptor whose name is not in here is simply not a strategy knob - that
     * is how the deployment-scope ones are excluded without hardcoding a group
     * name, and how a knob an admin invents through the API stays out of a shape
     * that has no column behind it.
     */
    private static final Set<String> KNOB_NAMES = knobNamesInCatalogOrder();

    private final FixedParameterRepository repository;
    private final FixedParameterOptions options;

    public StrategyFixedParameters(FixedParameterRepository repository,
                                   FixedParameterOptions options) {
        this.repository = repository;
        this.options = options;
    }

    /** The names this class recognizes as belonging to a strategy. */
    public static Set<String> knobNames() {
        return KNOB_NAMES;
    }

    /**
     * One reusable read of the catalog, for a response that renders the same form
     * more than once.
     *
     * The descriptors and their options are IDENTICAL every time - reading them
     * per use is a round trip for an answer that does not vary. A snapshot reads
     * the catalog once, carries one {@link FixedParameterOptions.Snapshot} with
     * it, and answers the rest from memory.
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

        /** The strategy-scope sections, in catalog order. */
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
     * The fields a builder form draws, grouped by section and ordered the way the
     * catalog is - by group, then position within it, then name.
     *
     * A form drawing a blank template reads these; reopened on a saved strategy it
     * reads the same descriptors and fills them from the strategy's flat fields,
     * matched by {@code name}.
     *
     * @return an empty list if the catalog has been emptied; never null
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
            if (KNOB_NAMES.contains(row.getName())) {
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
     * Written in the order the columns appear on a strategy, so a reader can check
     * this list against the response shape at a glance. The set is what is used;
     * the order only matters to whoever maintains it.
     */
    private static Set<String> knobNamesInCatalogOrder() {
        return Set.copyOf(new LinkedHashSet<>(List.of(
                // The ticker, not the id: it is what the knob's options offer and
                // what a PUT carries.
                "symbol",
                // Derived, not stored: a strategy has no exchange column, it has a
                // symbol and the symbol has a venue.
                "exchangeCode",
                "candleDuration",
                "triggerDuration",
                "derivative",
                "ceEnabled",
                "ceMoneyness",
                "ceStrikeOffset",
                "peEnabled",
                "peMoneyness",
                "peStrikeOffset",
                "lotRule",
                "baseLot",
                "averagingCount",
                "slPct",
                "tpPct")));
    }
}

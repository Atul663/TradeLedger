package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.StrategyLegView;
import com.example.tradeLedger.entity.Derivative;
import com.example.tradeLedger.entity.LotRule;
import com.example.tradeLedger.entity.Moneyness;
import com.example.tradeLedger.entity.UserStrategy;
import com.example.tradeLedger.exception.StrategyValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The rules the typed columns of {@link UserStrategy} have to satisfy.
 *
 * The database already refuses the worst of it - the CHECK constraints on the
 * entity mean a strike depth cannot disagree with its moneyness however the row
 * is written. This layer exists to catch the same mistakes one request earlier
 * and say something useful about them, and to enforce the cross-column rules a
 * CHECK cannot express as readably: a FUT strategy having no option side, an
 * OPTION strategy having at least one.
 *
 * Every method collects errors rather than throwing on the first, so a form with
 * three problems reports three.
 */
@Component
public class UserStrategyValidator {

    /**
     * Parses an enum from the wire, case-insensitively, with a message that lists
     * the alternatives instead of leaking a Java exception.
     */
    public static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(normalized)) {
                return constant;
            }
        }
        throw new StrategyValidationException(field + " must be one of "
                + Arrays.toString(type.getEnumConstants()) + ", got '" + value + "'");
    }

    /**
     * Checks a fully populated strategy, after every field has been applied.
     *
     * Ordering matters: a leg cannot be judged without knowing the derivative, and
     * the derivative cannot be judged without knowing which legs are on, so this
     * runs once at the end rather than per setter.
     */
    public void validate(UserStrategy strategy) {
        List<String> errors = new ArrayList<>();

        validateInstrument(strategy, errors);
        validateSizing(strategy, errors);
        validateExits(strategy, errors);

        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }
    }

    private void validateInstrument(UserStrategy strategy, List<String> errors) {
        if (strategy.getDerivative() == Derivative.FUT) {
            if (strategy.isCeEnabled() || strategy.isPeEnabled()) {
                errors.add("derivative is FUT, so no CE or PE side applies - "
                        + "turn them off, or set derivative to OPTION");
            }
            return;
        }
        if (!strategy.isCeEnabled() && !strategy.isPeEnabled()) {
            errors.add("derivative is OPTION but neither side is on - "
                    + "enable ceEnabled, peEnabled, or both");
        }
        validateSide(errors, "ce", strategy.isCeEnabled(),
                strategy.getCeMoneyness(), strategy.getCeStrikeOffset());
        validateSide(errors, "pe", strategy.isPeEnabled(),
                strategy.getPeMoneyness(), strategy.getPeStrikeOffset());
    }

    /**
     * ATM is a single strike; ITM and OTM offer fifteen each. "OTM0" is not a
     * thing - that is what ATM is called.
     */
    private void validateSide(List<String> errors, String side, boolean enabled,
                              Moneyness moneyness, int strikeOffset) {
        if (!enabled) {
            return;
        }
        if (moneyness == null) {
            errors.add(side + "Moneyness is required while " + side + "Enabled is true (ATM, ITM or OTM)");
            return;
        }
        if (moneyness == Moneyness.ATM) {
            if (strikeOffset != 0) {
                errors.add(side + "StrikeOffset must be 0 for ATM - there is one at-the-money strike; "
                        + "use ITM or OTM to move " + strikeOffset + " strike(s) away");
            }
            return;
        }
        if (strikeOffset < 1 || strikeOffset > UserStrategy.MAX_STRIKE_OFFSET) {
            errors.add(side + "StrikeOffset must be 1.." + UserStrategy.MAX_STRIKE_OFFSET
                    + " for " + moneyness + ", got " + strikeOffset);
        }
    }

    private void validateSizing(UserStrategy strategy, List<String> errors) {
        if (strategy.getBaseLot() < 1) {
            errors.add("baseLot must be at least 1, got " + strategy.getBaseLot());
        }
        int averaging = strategy.getAveragingCount();
        if (averaging < 0 || averaging > UserStrategy.MAX_AVERAGING_COUNT) {
            errors.add("averagingCount must be 0.." + UserStrategy.MAX_AVERAGING_COUNT
                    + ", got " + averaging);
        }
        // A ladder with nothing to climb is a configuration the user did not mean:
        // FIXED and DOUBLE are indistinguishable at zero adds.
        if (averaging == 0 && strategy.getLotRule() != LotRule.FIXED) {
            errors.add("lotRule " + strategy.getLotRule() + " has no effect while averagingCount is 0 - "
                    + "raise averagingCount, or set lotRule to FIXED");
        }
    }

    private void validateExits(UserStrategy strategy, List<String> errors) {
        checkPercent(errors, "slPct", strategy.getSlPct());
        checkPercent(errors, "tpPct", strategy.getTpPct());
    }

    private void checkPercent(List<String> errors, String field, BigDecimal value) {
        if (value == null) {
            return;
        }
        if (value.signum() <= 0) {
            errors.add(field + " must be greater than 0, got " + value.toPlainString());
        } else if (value.compareTo(new BigDecimal("100")) > 0) {
            errors.add(field + " must be at most 100, got " + value.toPlainString());
        }
    }

    // ----------------------------------------------------------------- views

    /**
     * The legs this strategy trades, derived from the columns for display.
     *
     * One place builds this so a leg is described identically wherever it
     * appears - the saved strategy, the runtime view, and every deployment of it.
     */
    public List<StrategyLegView> legs(UserStrategy strategy) {
        List<StrategyLegView> legs = new ArrayList<>(2);
        if (strategy.getDerivative() == Derivative.FUT) {
            legs.add(new StrategyLegView("FUT", null, 0, "FUT"));
            return legs;
        }
        if (strategy.isCeEnabled()) {
            legs.add(leg("CE", strategy.getCeMoneyness(), strategy.getCeStrikeOffset()));
        }
        if (strategy.isPeEnabled()) {
            legs.add(leg("PE", strategy.getPeMoneyness(), strategy.getPeStrikeOffset()));
        }
        return legs;
    }

    private StrategyLegView leg(String side, Moneyness moneyness, int strikeOffset) {
        return new StrategyLegView(side, moneyness == null ? null : moneyness.name(), strikeOffset,
                UserStrategy.legLabel(side, moneyness, strikeOffset));
    }
}

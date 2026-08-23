package com.example.tradeLedger;

import com.example.tradeLedger.dto.StrategyLegView;
import com.example.tradeLedger.entity.Derivative;
import com.example.tradeLedger.entity.LotRule;
import com.example.tradeLedger.entity.Moneyness;
import com.example.tradeLedger.entity.UserStrategy;
import com.example.tradeLedger.exception.StrategyValidationException;
import com.example.tradeLedger.serviceImpl.UserStrategyValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cross-column rules a CHECK constraint cannot state readably: a FUT strategy
 * with no option side, an OPTION strategy with at least one, and a strike depth
 * that agrees with its moneyness.
 */
class UserStrategyValidatorTest {

    private final UserStrategyValidator validator = new UserStrategyValidator();

    /** A call at OTM1 and a put at ATM - the configuration the spreadsheet could not express. */
    private static UserStrategy bothSides() {
        UserStrategy strategy = new UserStrategy();
        strategy.setDerivative(Derivative.OPTION);
        strategy.setCeEnabled(true);
        strategy.setCeMoneyness(Moneyness.OTM);
        strategy.setCeStrikeOffset(1);
        strategy.setPeEnabled(true);
        strategy.setPeMoneyness(Moneyness.ATM);
        return strategy;
    }

    @Test
    void acceptsACallAndAPutAtDifferentStrikes() {
        UserStrategy strategy = bothSides();
        validator.validate(strategy);

        List<StrategyLegView> legs = validator.legs(strategy);
        assertEquals(2, legs.size());
        assertEquals("CE OTM1", legs.get(0).label());
        assertEquals("PE ATM", legs.get(1).label());
    }

    @Test
    void describesAFutureAsASingleLegWithNoStrike() {
        UserStrategy strategy = new UserStrategy();
        strategy.setDerivative(Derivative.FUT);
        validator.validate(strategy);

        assertEquals(List.of("FUT"), validator.legs(strategy).stream().map(StrategyLegView::label).toList());
    }

    @Test
    void rejectsAnOptionStrategyWithNeitherSideOn() {
        UserStrategy strategy = new UserStrategy();
        strategy.setDerivative(Derivative.OPTION);

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> validator.validate(strategy));
        assertTrue(thrown.getMessage().contains("neither side"), thrown.getMessage());
    }

    @Test
    void rejectsAFutureStrategyThatStillCarriesAnOptionSide() {
        UserStrategy strategy = bothSides();
        strategy.setDerivative(Derivative.FUT);

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> validator.validate(strategy));
        assertTrue(thrown.getMessage().contains("FUT"), thrown.getMessage());
    }

    /** There is exactly one at-the-money strike, so OTM0 is not a way to spell it. */
    @Test
    void rejectsADepthOnAnAtTheMoneySide() {
        UserStrategy strategy = bothSides();
        strategy.setPeStrikeOffset(3);

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> validator.validate(strategy));
        assertTrue(thrown.getMessage().contains("peStrikeOffset"), thrown.getMessage());
    }

    @Test
    void acceptsEveryDepthTheChainOffers() {
        UserStrategy strategy = bothSides();
        for (int offset = 1; offset <= UserStrategy.MAX_STRIKE_OFFSET; offset++) {
            strategy.setCeStrikeOffset(offset);
            validator.validate(strategy);
        }
        assertEquals(15, UserStrategy.MAX_STRIKE_OFFSET);
    }

    @Test
    void rejectsADepthPastTheEndOfTheChain() {
        UserStrategy strategy = bothSides();
        strategy.setCeStrikeOffset(16);

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> validator.validate(strategy));
        assertTrue(thrown.getMessage().contains("1..15"), thrown.getMessage());
    }

    @Test
    void rejectsASideTurnedOnWithNoMoneynessPicked() {
        UserStrategy strategy = new UserStrategy();
        strategy.setDerivative(Derivative.OPTION);
        strategy.setCeEnabled(true);

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> validator.validate(strategy));
        assertTrue(thrown.getMessage().contains("ceMoneyness"), thrown.getMessage());
    }

    /** FIXED and DOUBLE are indistinguishable at zero adds, so the pairing is a mistake. */
    @Test
    void rejectsALadderWithNothingToClimb() {
        UserStrategy strategy = bothSides();
        strategy.setLotRule(LotRule.DOUBLE);
        strategy.setAveragingCount(0);

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> validator.validate(strategy));
        assertTrue(thrown.getMessage().contains("averagingCount"), thrown.getMessage());
    }

    @Test
    void acceptsTheSpreadsheetLadder() {
        UserStrategy strategy = bothSides();
        strategy.setLotRule(LotRule.DOUBLE);
        strategy.setBaseLot(65);
        strategy.setAveragingCount(2);

        validator.validate(strategy);
    }

    @Test
    void rejectsANonsensicalStopLoss() {
        UserStrategy strategy = bothSides();
        strategy.setSlPct(new BigDecimal("0"));

        StrategyValidationException thrown =
                assertThrows(StrategyValidationException.class, () -> validator.validate(strategy));
        assertTrue(thrown.getMessage().contains("slPct"), thrown.getMessage());
    }

    @Test
    void namesTheAlternativesWhenAnEnumDoesNotParse() {
        StrategyValidationException thrown = assertThrows(StrategyValidationException.class,
                () -> UserStrategyValidator.parse(Moneyness.class, "DEEP", "ceMoneyness"));

        assertTrue(thrown.getMessage().contains("ATM"), thrown.getMessage());
    }

    @Test
    void parsesEnumsCaseInsensitively() {
        assertEquals(Moneyness.OTM, UserStrategyValidator.parse(Moneyness.class, " otm ", "ceMoneyness"));
    }
}

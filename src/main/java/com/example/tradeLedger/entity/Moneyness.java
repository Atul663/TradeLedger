package com.example.tradeLedger.entity;

/**
 * Where an option's strike sits relative to the underlying's current price.
 *
 * Paired with a depth ({@code strikeOffset}) everywhere it is used: ITM and OTM
 * take 1..15, ATM takes none, because there is exactly one at-the-money strike
 * and "OTM0" is just another name for it.
 *
 * A depth rather than a price, so the choice survives the underlying moving:
 * "three strikes out of the money" is still meaningful tomorrow morning, a pinned
 * 25000 CE is not.
 */
public enum Moneyness {

    /** The strike nearest the underlying's price. */
    ATM,

    /** Strikes with intrinsic value - below spot for a call, above it for a put. */
    ITM,

    /** Strikes with no intrinsic value - above spot for a call, below it for a put. */
    OTM
}

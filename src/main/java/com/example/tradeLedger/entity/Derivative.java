package com.example.tradeLedger.entity;

/**
 * What kind of contract the signal is traded through.
 *
 * The indicators always run on the UNDERLYING - an index or a stock - because
 * that is where the price action is. This says what the order is actually placed
 * on once the signal fires.
 */
public enum Derivative {

    /** The future. One position, no strike to choose - the sheet's "NA" row. */
    FUTURES,

    /** Options: a call, a put, or both, each picking its own strike. */
    OPTION
}

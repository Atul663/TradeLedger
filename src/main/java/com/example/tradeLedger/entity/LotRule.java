package com.example.tradeLedger.entity;

/**
 * How the order size progresses across averaging entries.
 *
 * The strategy enters once on the signal, then adds up to
 * {@code averagingCount} more times as the position moves against it. This says
 * how big each of those adds is, starting from {@code baseLot}:
 *
 * <pre>
 *   baseLot = 65,  averagingCount = 2
 *
 *   FIXED       65    65    65        every add the same size
 *   DOUBLE      65   130   260        each add doubles the one before
 *   CUMULATIVE  65   130   195        each add is one baseLot more than the last
 * </pre>
 *
 * The ladder is applied by the execution engine, not here - this column only
 * records which of the three the user picked.
 */
public enum LotRule {

    /** Same size every time. */
    FIXED,

    /** Each entry doubles the previous one. */
    DOUBLE,

    /** Each entry adds one more base lot than the previous one. */
    CUMULATIVE
}

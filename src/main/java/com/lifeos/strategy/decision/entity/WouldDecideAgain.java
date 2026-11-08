package com.lifeos.strategy.decision.entity;

/** The look-back on a decision, separating the quality of the call from how things turned out. */
public enum WouldDecideAgain {
    YES,
    NO,
    /** The decision was right with what was known; the result was bad luck. */
    GOOD_CALL_BAD_LUCK
}

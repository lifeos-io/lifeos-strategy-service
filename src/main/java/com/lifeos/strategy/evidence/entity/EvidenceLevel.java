package com.lifeos.strategy.evidence.entity;

/** The steps of the evidence chain, lowest first: an activity leads to an output, then an outcome, then impact. */
public enum EvidenceLevel {
    /** Something done, e.g. "wrote the gateway rate limit". */
    ACTIVITY,
    /** Something produced, e.g. "lifeos-gateway 0.1.0 released". */
    OUTPUT,
    /** A result in the world, e.g. "the code review praised it". */
    OUTCOME,
    /** What changed because of it, e.g. "got the in-house offer". */
    IMPACT;

    /** Whether evidence of this level can build on {@code parent} (only on a lower step). */
    public boolean canBuildOn(EvidenceLevel parent) {
        return parent.ordinal() < ordinal();
    }
}

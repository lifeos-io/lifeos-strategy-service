package com.lifeos.strategy.knowledge.entity;

public enum KnowledgeStatus {
    UNVERIFIED,
    PARTIAL,
    VERIFIED,
    FALSIFIED;

    /** Verified or falsified: the question is answered (for now). */
    public boolean isSettled() {
        return this == VERIFIED || this == FALSIFIED;
    }
}

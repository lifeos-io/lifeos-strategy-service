package com.lifeos.strategy.decision.entity;

public enum TriggerKind {
    /** Reconsider on a given date. */
    DATE,
    /** Reconsider when something happens, e.g. "if I get assigned to the K8s project". */
    EVENT
}

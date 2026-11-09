package com.lifeos.strategy.audit.entity;

/** Who caused an audited operation. */
public enum AuditActor {
    /** The user, through their own request. */
    USER,
    /** A background step, e.g. an event consumer. */
    SYSTEM
}

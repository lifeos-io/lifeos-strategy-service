package com.lifeos.strategy.audit.entity;

/** Sensitive operations that leave an audit record. */
public enum AuditAction {
    /** All of the user's Strategy data was deleted (KVKK); details: rows deleted per table. */
    USER_DATA_DELETED,
    /** The user's identity account (Keycloak) was deleted, or was already gone. */
    IDENTITY_ACCOUNT_DELETED,
    /** Deleting the identity account failed after every retry; it needs a person to look at it. */
    IDENTITY_ACCOUNT_DELETION_FAILED
}

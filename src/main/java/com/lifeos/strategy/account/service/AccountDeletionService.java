package com.lifeos.strategy.account.service;

import java.util.UUID;

/**
 * KVKK deletion. Strategy deletes all of the user's data at once, records it in the audit log and publishes
 * {@code UserDeletionRequested}, on which the other services delete theirs and the identity account is deleted.
 * Deleting again is harmless: nothing is left to delete, and the event is published again.
 */
public interface AccountDeletionService {

    void delete(UUID userId);

    /** Deletes the user's identity account (on {@code UserDeletionRequested}) and audits it. */
    void deleteIdentity(UUID userId);

    /** Audits that the identity account could not be deleted after every retry. */
    void recordIdentityDeletionFailed(UUID userId);
}

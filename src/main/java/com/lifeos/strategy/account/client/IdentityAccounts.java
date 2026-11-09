package com.lifeos.strategy.account.client;

import java.util.UUID;

/** The users' identity accounts (Keycloak), as far as Strategy needs them: deleting one after a KVKK deletion. */
public interface IdentityAccounts {

    /**
     * Deletes the user's identity account. Safe to repeat: an account that is already gone counts as deleted.
     *
     * @throws RuntimeException when the identity provider cannot be reached or refuses (the caller retries)
     */
    IdentityDeletion delete(UUID userId);

    enum IdentityDeletion {
        /** The account existed and was deleted now. */
        DELETED,
        /** There was no such account (deleted earlier, e.g. by a previous attempt). */
        ALREADY_GONE
    }
}

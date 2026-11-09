package com.lifeos.strategy.account.event;

import java.time.Instant;

/**
 * Payload of {@code UserDeletionRequested} v1 (lifeos-contracts: schemas/strategy/UserDeletionRequested.v1.schema.json).
 * The user is the envelope's {@code userId}; the payload carries no personal data.
 */
public record UserDeletionRequestedV1(Instant requestedAt) {

    public static final String TYPE = "UserDeletionRequested";
    public static final int VERSION = 1;
}

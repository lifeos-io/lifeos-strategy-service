package com.lifeos.strategy.profile.event;

import java.time.Instant;

/** Payload of {@code UserProfileUpdated} v1 (lifeos-contracts: schemas/strategy/UserProfileUpdated.v1.schema.json). */
public record UserProfileUpdatedV1(
        String displayName,
        String timezone,
        String locale,
        int defaultWeeklyMinutes,
        Instant onboardingCompletedAt) {

    public static final String TYPE = "UserProfileUpdated";
    public static final int VERSION = 1;
}

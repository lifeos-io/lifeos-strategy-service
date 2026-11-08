package com.lifeos.strategy.profile.dto;

import java.time.Instant;
import java.util.UUID;

public record ProfileResponse(
        UUID userId,
        String displayName,
        String timezone,
        String locale,
        int defaultWeeklyMinutes,
        boolean onboardingCompleted,
        Instant onboardingCompletedAt) {
}

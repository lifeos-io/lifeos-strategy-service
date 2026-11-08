package com.lifeos.strategy.profile.dto;

import com.lifeos.strategy.common.validation.LanguageTag;
import com.lifeos.strategy.common.validation.TimeZoneId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /api/strategy/me}: only the fields present in the body change.
 *
 * @param onboardingCompleted {@code true} marks onboarding as done; it cannot be reset
 */
public record UpdateProfileRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String displayName,
        @TimeZoneId String timezone,
        @LanguageTag String locale,
        @Min(0) @Max(10080) Integer defaultWeeklyMinutes,
        Boolean onboardingCompleted) {
}

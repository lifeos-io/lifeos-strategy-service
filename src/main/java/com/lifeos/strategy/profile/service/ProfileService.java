package com.lifeos.strategy.profile.service;

import com.lifeos.strategy.profile.dto.ProfileResponse;
import com.lifeos.strategy.profile.dto.UpdateProfileRequest;
import java.util.UUID;

/**
 * The user profile. A user who signed up in Keycloak has no row yet: the first call creates one with defaults
 * (the mobile app then runs onboarding, which fills in the rest). Every creation and every real change publishes
 * {@code UserProfileUpdated}.
 */
public interface ProfileService {

    String DEFAULT_TIMEZONE = "Europe/Istanbul";
    String DEFAULT_LOCALE = "tr-TR";
    int DEFAULT_WEEKLY_MINUTES = 300;

    /** The user's profile, created with defaults on first use. */
    ProfileResponse getOrCreate(UUID userId);

    /** Partial update; creates the profile first if needed. */
    ProfileResponse update(UUID userId, UpdateProfileRequest request);
}

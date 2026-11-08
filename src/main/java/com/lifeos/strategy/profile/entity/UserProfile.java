package com.lifeos.strategy.profile.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The user's settings that the rest of Strategy (and, through {@code UserProfileUpdated}, every other service)
 * relies on: time zone, language and the default weekly capacity. One row per Keycloak user.
 */
@Entity
@Table(name = "user_profile")
public class UserProfile {

    @Id
    private UUID userId;

    private String displayName;
    private String timezone;
    private String locale;
    private int defaultWeeklyMinutes;
    private Instant onboardingCompletedAt;

    @Version
    private int version;

    private Instant createdAt;
    private Instant updatedAt;

    protected UserProfile() {
    }

    /**
     * Partial update: {@code null} arguments leave the field as it is. Onboarding can only be completed once.
     *
     * @return whether anything changed
     */
    public boolean update(String displayName, String timezone, String locale, Integer defaultWeeklyMinutes,
            boolean completeOnboarding, Instant now) {
        boolean changed = false;
        if (displayName != null && !displayName.equals(this.displayName)) {
            this.displayName = displayName;
            changed = true;
        }
        if (timezone != null && !timezone.equals(this.timezone)) {
            this.timezone = timezone;
            changed = true;
        }
        if (locale != null && !locale.equals(this.locale)) {
            this.locale = locale;
            changed = true;
        }
        if (defaultWeeklyMinutes != null && defaultWeeklyMinutes != this.defaultWeeklyMinutes) {
            this.defaultWeeklyMinutes = defaultWeeklyMinutes;
            changed = true;
        }
        if (completeOnboarding && onboardingCompletedAt == null) {
            onboardingCompletedAt = now;
            changed = true;
        }
        if (changed) {
            updatedAt = Objects.requireNonNull(now);
        }
        return changed;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getLocale() {
        return locale;
    }

    public int getDefaultWeeklyMinutes() {
        return defaultWeeklyMinutes;
    }

    public Instant getOnboardingCompletedAt() {
        return onboardingCompletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

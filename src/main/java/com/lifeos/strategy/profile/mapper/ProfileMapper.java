package com.lifeos.strategy.profile.mapper;

import com.lifeos.strategy.profile.dto.ProfileResponse;
import com.lifeos.strategy.profile.entity.UserProfile;
import com.lifeos.strategy.profile.event.UserProfileUpdatedV1;
import org.springframework.stereotype.Component;

@Component
public class ProfileMapper {

    public ProfileResponse toResponse(UserProfile profile) {
        return new ProfileResponse(profile.getUserId(), profile.getDisplayName(), profile.getTimezone(),
                profile.getLocale(), profile.getDefaultWeeklyMinutes(), profile.getOnboardingCompletedAt() != null,
                profile.getOnboardingCompletedAt());
    }

    public UserProfileUpdatedV1 toUpdatedEvent(UserProfile profile) {
        return new UserProfileUpdatedV1(profile.getDisplayName(), profile.getTimezone(), profile.getLocale(),
                profile.getDefaultWeeklyMinutes(), profile.getOnboardingCompletedAt());
    }
}

package com.lifeos.strategy.profile.service.impl;

import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.strategy.profile.dto.ProfileResponse;
import com.lifeos.strategy.profile.dto.UpdateProfileRequest;
import com.lifeos.strategy.profile.entity.UserProfile;
import com.lifeos.strategy.profile.event.UserProfileUpdatedV1;
import com.lifeos.strategy.profile.mapper.ProfileMapper;
import com.lifeos.strategy.profile.repository.UserProfileRepository;
import com.lifeos.strategy.profile.service.ProfileService;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Events go through the outbox in the same transaction as the change. */
@Service
public class ProfileServiceImpl implements ProfileService {

    private final UserProfileRepository repository;
    private final ProfileMapper profileMapper;
    private final Outbox outbox;
    private final Clock clock;

    public ProfileServiceImpl(UserProfileRepository repository, ProfileMapper profileMapper, Outbox outbox,
            Clock clock) {
        this.repository = repository;
        this.profileMapper = profileMapper;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProfileResponse getOrCreate(UUID userId) {
        return profileMapper.toResponse(loadOrCreate(userId));
    }

    @Override
    @Transactional
    public ProfileResponse update(UUID userId, UpdateProfileRequest request) {
        UserProfile profile = loadOrCreate(userId);
        boolean changed = profile.update(
                request.displayName() == null ? null : request.displayName().strip(),
                request.timezone(),
                request.locale() == null ? null : Locale.forLanguageTag(request.locale()).toLanguageTag(),
                request.defaultWeeklyMinutes(),
                Boolean.TRUE.equals(request.onboardingCompleted()),
                clock.instant());
        if (changed) {
            publishUpdated(profile);
        }
        return profileMapper.toResponse(profile);
    }

    private UserProfile loadOrCreate(UUID userId) {
        int created = repository.insertIfAbsent(userId, DEFAULT_TIMEZONE, DEFAULT_LOCALE, DEFAULT_WEEKLY_MINUTES,
                clock.instant());
        UserProfile profile = repository.findById(userId).orElseThrow();
        if (created == 1) {
            publishUpdated(profile);
        }
        return profile;
    }

    private void publishUpdated(UserProfile profile) {
        outbox.append(new OutboxMessage("UserProfile", profile.getUserId(), profile.getUserId(),
                UserProfileUpdatedV1.TYPE, UserProfileUpdatedV1.VERSION, profileMapper.toUpdatedEvent(profile)));
    }
}

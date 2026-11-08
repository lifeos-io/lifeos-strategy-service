package com.lifeos.strategy.feedback.service.impl;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.strategy.common.error.FieldErrors;
import com.lifeos.strategy.common.time.UserDates;
import com.lifeos.strategy.feedback.dto.FeedbackResponse;
import com.lifeos.strategy.feedback.dto.RecordFeedbackRequest;
import com.lifeos.strategy.feedback.entity.Feedback;
import com.lifeos.strategy.feedback.event.FeedbackRecordedV1;
import com.lifeos.strategy.feedback.mapper.FeedbackMapper;
import com.lifeos.strategy.feedback.repository.FeedbackRepository;
import com.lifeos.strategy.feedback.service.FeedbackService;
import com.lifeos.strategy.profile.dto.ProfileResponse;
import com.lifeos.strategy.profile.service.ProfileService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository repository;
    private final ProfileService profileService;
    private final FeedbackMapper feedbackMapper;
    private final Outbox outbox;
    private final Clock clock;

    public FeedbackServiceImpl(FeedbackRepository repository, ProfileService profileService,
            FeedbackMapper feedbackMapper, Outbox outbox, Clock clock) {
        this.repository = repository;
        this.profileService = profileService;
        this.feedbackMapper = feedbackMapper;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public FeedbackResponse record(UUID userId, RecordFeedbackRequest request) {
        // Also creates the profile (foreign key) and gives the user's time zone for "today".
        ProfileResponse profile = profileService.getOrCreate(userId);
        if (request.receivedOn().isAfter(UserDates.today(clock, profile.timezone()))) {
            throw FieldErrors.invalid("receivedOn", "must not be in the future");
        }
        Feedback feedback = Feedback.record(userId, request.sourceKind(), strip(request.sourceLabel()),
                request.content().strip(), request.reliability(), request.receivedOn(), clock.instant());
        repository.saveAndFlush(feedback);
        outbox.append(new OutboxMessage("Feedback", feedback.getId(), userId, FeedbackRecordedV1.TYPE,
                FeedbackRecordedV1.VERSION, feedbackMapper.toRecordedEvent(feedback)));
        return feedbackMapper.toResponse(feedback);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackResponse> list(UUID userId) {
        return repository.findByUserIdOrderByReceivedOnDescCreatedAtDesc(userId).stream()
                .map(feedbackMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackResponse get(UUID userId, UUID feedbackId) {
        return repository.findByIdAndUserId(feedbackId, userId)
                .map(feedbackMapper::toResponse)
                .orElseThrow(() -> new LifeOsException(CommonErrorCode.NOT_FOUND, "Feedback not found."));
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}

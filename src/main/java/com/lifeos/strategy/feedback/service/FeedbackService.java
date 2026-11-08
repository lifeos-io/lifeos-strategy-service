package com.lifeos.strategy.feedback.service;

import com.lifeos.strategy.feedback.dto.FeedbackResponse;
import com.lifeos.strategy.feedback.dto.RecordFeedbackRequest;
import java.util.List;
import java.util.UUID;

/** Feedback the user received; append-only. Recording publishes {@code FeedbackRecorded}. */
public interface FeedbackService {

    FeedbackResponse record(UUID userId, RecordFeedbackRequest request);

    /** Most recently received first. */
    List<FeedbackResponse> list(UUID userId);

    FeedbackResponse get(UUID userId, UUID feedbackId);
}

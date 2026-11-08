package com.lifeos.strategy.feedback.mapper;

import com.lifeos.strategy.feedback.dto.FeedbackResponse;
import com.lifeos.strategy.feedback.entity.Feedback;
import com.lifeos.strategy.feedback.event.FeedbackRecordedV1;
import org.springframework.stereotype.Component;

@Component
public class FeedbackMapper {

    public FeedbackResponse toResponse(Feedback feedback) {
        return new FeedbackResponse(feedback.getId(), feedback.getSourceKind(), feedback.getSourceLabel(),
                feedback.getContent(), feedback.getReliability(), feedback.getReceivedOn(), feedback.getCreatedAt());
    }

    public FeedbackRecordedV1 toRecordedEvent(Feedback feedback) {
        return new FeedbackRecordedV1(feedback.getId(), feedback.getSourceKind(), feedback.getSourceLabel(),
                feedback.getContent(), feedback.getReliability(), feedback.getReceivedOn());
    }
}

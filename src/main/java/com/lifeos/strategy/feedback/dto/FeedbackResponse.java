package com.lifeos.strategy.feedback.dto;

import com.lifeos.strategy.feedback.entity.FeedbackSourceKind;
import com.lifeos.strategy.feedback.entity.Reliability;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FeedbackResponse(
        UUID id,
        FeedbackSourceKind sourceKind,
        String sourceLabel,
        String content,
        Reliability reliability,
        LocalDate receivedOn,
        Instant createdAt) {
}

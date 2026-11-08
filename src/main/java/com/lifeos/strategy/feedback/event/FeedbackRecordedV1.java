package com.lifeos.strategy.feedback.event;

import com.lifeos.strategy.feedback.entity.FeedbackSourceKind;
import com.lifeos.strategy.feedback.entity.Reliability;
import java.time.LocalDate;
import java.util.UUID;

/** Payload of {@code FeedbackRecorded} v1 (lifeos-contracts: schemas/strategy/FeedbackRecorded.v1.schema.json). */
public record FeedbackRecordedV1(
        UUID feedbackId,
        FeedbackSourceKind sourceKind,
        String sourceLabel,
        String content,
        Reliability reliability,
        LocalDate receivedOn) {

    public static final String TYPE = "FeedbackRecorded";
    public static final int VERSION = 1;
}

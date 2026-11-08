package com.lifeos.strategy.feedback.dto;

import com.lifeos.strategy.feedback.entity.FeedbackSourceKind;
import com.lifeos.strategy.feedback.entity.Reliability;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code POST /api/strategy/feedback}.
 *
 * @param sourceLabel who it came from in the user's words, e.g. "team lead"
 * @param receivedOn  not in the future (in the user's time zone)
 */
public record RecordFeedbackRequest(
        @NotNull FeedbackSourceKind sourceKind,
        @Size(max = 200) String sourceLabel,
        @NotBlank @Size(max = 4000) String content,
        @NotNull Reliability reliability,
        @NotNull LocalDate receivedOn) {
}

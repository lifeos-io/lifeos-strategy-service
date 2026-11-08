package com.lifeos.strategy.decision.dto;

import com.lifeos.strategy.decision.entity.WouldDecideAgain;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/strategy/decisions/{id}/close}: the look-back (OPEN → CLOSED).
 *
 * @param outcomeNote      what actually happened
 * @param wouldDecideAgain the quality of the call, apart from the outcome
 */
public record CloseDecisionRequest(
        @NotNull Integer version,
        @NotBlank @Size(max = 4000) String outcomeNote,
        @NotNull WouldDecideAgain wouldDecideAgain) {
}

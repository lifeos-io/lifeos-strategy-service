package com.lifeos.strategy.decision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code POST /api/strategy/decisions/{id}/decide}: commit to one alternative (DRAFT → OPEN).
 *
 * @param chosenLabel     the alternative's label, e.g. {@code B}
 * @param expectedOutcome what the user expects to happen, to compare with later
 * @param reviewOn        when to look back, in the future
 */
public record DecideRequest(
        @NotNull Integer version,
        @NotBlank @Pattern(regexp = "[A-Z]") String chosenLabel,
        @NotBlank @Size(max = 2000) String expectedOutcome,
        @NotNull LocalDate reviewOn) {
}

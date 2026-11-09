package com.lifeos.strategy.decision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/strategy/decisions/{id}/reopen}: weigh a decided decision again, as a new draft.
 *
 * @param reason why it is reopened, e.g. "the assumption that recruiters only read LinkedIn was wrong"
 */
public record ReopenDecisionRequest(@NotBlank @Size(max = 2000) String reason) {
}

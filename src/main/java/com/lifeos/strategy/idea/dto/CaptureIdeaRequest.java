package com.lifeos.strategy.idea.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code POST /api/strategy/ideas}: capture an idea into the INBOX. Kept short on purpose: capturing must be quick.
 *
 * @param goalId the goal the idea would serve, if any (one of the user's open goals)
 */
public record CaptureIdeaRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String note,
        UUID goalId) {
}

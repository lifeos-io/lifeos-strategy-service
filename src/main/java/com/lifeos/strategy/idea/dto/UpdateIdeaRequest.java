package com.lifeos.strategy.idea.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code PATCH /api/strategy/ideas/{ideaId}}: only the fields present change. Dropped and promoted ideas are
 * read-only.
 *
 * @param version the version the client last read (409 {@code CONCURRENT_MODIFICATION} when it is stale)
 */
public record UpdateIdeaRequest(
        @NotNull Integer version,
        @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
        @Size(max = 4000) String note,
        UUID goalId) {
}

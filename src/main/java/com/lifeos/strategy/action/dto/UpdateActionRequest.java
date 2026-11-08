package com.lifeos.strategy.action.dto;

import com.lifeos.strategy.action.entity.ActionKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/**
 * {@code PATCH /api/strategy/weekly-plans/{isoWeek}/actions/{actionId}}: the content of an action; only the
 * fields present change, {@code goalIds} replaces the goals. The status has its own endpoint.
 *
 * @param version the version the client last read (409 {@code CONCURRENT_MODIFICATION} when it is stale)
 */
public record UpdateActionRequest(
        @NotNull Integer version,
        @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
        @Size(max = 1000) String why,
        @Size(max = 1000) String expectedEvidence,
        ActionKind kind,
        @Min(0) @Max(10080) Integer estimateMinutes,
        @Size(min = 1, max = 10) Set<@NotNull UUID> goalIds,
        UUID phaseKey) {
}

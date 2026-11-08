package com.lifeos.strategy.evidence.dto;

import com.lifeos.strategy.evidence.entity.EvidenceLevel;
import com.lifeos.strategy.evidence.entity.EvidenceStrength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * {@code POST /api/strategy/evidence}.
 *
 * @param occurredOn       not in the future (in the user's time zone)
 * @param parentEvidenceId the lower step of the chain this builds on (e.g. the activity behind an output)
 * @param actionId         the action it came from; its goals are used when {@code goalIds} is empty
 * @param feedbackId       the feedback it came from (e.g. a code review that confirms an outcome)
 * @param goalIds          the user's goals it moves forward
 * @param phaseKeys        phases of those goals' current strategies whose exit criteria it counts towards
 */
public record RecordEvidenceRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull EvidenceLevel level,
        @NotNull EvidenceStrength strength,
        @NotNull LocalDate occurredOn,
        UUID parentEvidenceId,
        UUID actionId,
        UUID feedbackId,
        @Size(max = 2000) String note,
        @Size(max = 10) Set<@NotNull UUID> goalIds,
        @Size(max = 10) Set<@NotNull UUID> phaseKeys) {
}

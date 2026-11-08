package com.lifeos.strategy.action.dto;

import com.lifeos.strategy.action.entity.ActionKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/**
 * {@code POST /api/strategy/weekly-plans/{isoWeek}/actions}: a new PLANNED action at the end of the week.
 *
 * @param goalIds  goals the action serves; each must have minutes in this week's plan
 * @param phaseKey a phase of the current strategy of one of those goals, if the action moves one forward
 */
public record CreateActionRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String why,
        @Size(max = 1000) String expectedEvidence,
        @NotNull ActionKind kind,
        @NotNull @Min(0) @Max(10080) Integer estimateMinutes,
        @NotEmpty @Size(max = 10) Set<@NotNull UUID> goalIds,
        UUID phaseKey) {
}

package com.lifeos.strategy.strategyversion.dto;

import com.lifeos.strategy.strategyversion.entity.EffortCategory;
import com.lifeos.strategy.strategyversion.entity.PhaseStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code POST /api/strategy/goals/{goalId}/strategy-versions}: the goal's first strategy or a revision, always
 * as the complete new version.
 *
 * @param expectedCurrentVersionNo the current version the client based this on; {@code null} for the first
 *                                 strategy. A different current version means someone revised in between (409)
 * @param changeReason             why the strategy changes; required for a revision, optional for the first one
 * @param phases                   in order; ordinals become 1, 2, 3...
 * @param effortAllocation         percent per category, adding up to 100; a missing category counts as 0
 */
public record CreateStrategyVersionRequest(
        Integer expectedCurrentVersionNo,
        @NotBlank @Size(max = 2000) String summary,
        @Size(max = 2000) String changeReason,
        @NotEmpty @Size(max = 12) List<@Valid @NotNull PhaseRequest> phases,
        @NotNull Map<@NotNull EffortCategory, @NotNull @Min(0) @Max(100) Integer> effortAllocation) {

    /**
     * @param phaseKey the key of a phase of the current version to carry it over; {@code null} for a new phase
     */
    public record PhaseRequest(
            UUID phaseKey,
            @NotBlank @Size(max = 200) String title,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn,
            @Size(max = 2000) String exitCriteria,
            @NotNull PhaseStatus status) {
    }
}

package com.lifeos.strategy.goal.dto;

import com.lifeos.strategy.goal.entity.GoalDomain;
import com.lifeos.strategy.goal.entity.GoalStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code PATCH /api/strategy/goals/{goalId}}: only the fields present in the body change.
 *
 * @param version the version the client last read; a different current version means someone else changed the
 *                goal in between (409 {@code CONCURRENT_MODIFICATION})
 * @param status  ACTIVE ⇄ PAUSED, or ACHIEVED / ABANDONED to close the goal for good
 */
public record UpdateGoalRequest(
        @NotNull Integer version,
        @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
        GoalDomain domain,
        @Size(max = 2000) String whyItMatters,
        @Size(max = 2000) String successDefinition,
        LocalDate windowStart,
        LocalDate windowEnd,
        GoalStatus status) {
}

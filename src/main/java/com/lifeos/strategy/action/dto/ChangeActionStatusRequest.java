package com.lifeos.strategy.action.dto;

import com.lifeos.strategy.action.entity.ActionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/strategy/weekly-plans/{isoWeek}/actions/{actionId}/status}; only in the active week.
 *
 * @param version the version the client last read (409 {@code CONCURRENT_MODIFICATION} when it is stale)
 */
public record ChangeActionStatusRequest(
        @NotNull Integer version,
        @NotNull ActionStatus status) {
}

package com.lifeos.strategy.action.event;

import com.lifeos.strategy.action.entity.ActionKind;
import com.lifeos.strategy.action.entity.ActionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Payload of {@code ActionStatusChanged} v1 (lifeos-contracts: schemas/strategy/ActionStatusChanged.v1.schema.json). */
public record ActionStatusChangedV1(
        UUID actionId,
        UUID weeklyPlanId,
        String isoWeek,
        List<UUID> goalIds,
        UUID phaseKey,
        ActionKind kind,
        int estimateMinutes,
        ActionStatus previousStatus,
        ActionStatus status,
        int deferredCount,
        Instant completedAt,
        WeekV1 week) {

    public static final String TYPE = "ActionStatusChanged";
    public static final int VERSION = 1;

    /** The week's actions after the change; {@code plannedMinutes} leaves out dropped actions. */
    public record WeekV1(int planned, int done, int deferred, int dropped, int plannedMinutes, int doneMinutes) {
    }
}

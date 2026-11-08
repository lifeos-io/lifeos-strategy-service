package com.lifeos.strategy.action.dto;

import com.lifeos.strategy.action.entity.ActionKind;
import com.lifeos.strategy.action.entity.ActionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActionResponse(
        UUID id,
        UUID weeklyPlanId,
        List<UUID> goalIds,
        UUID phaseKey,
        String title,
        String why,
        String expectedEvidence,
        ActionKind kind,
        int estimateMinutes,
        ActionStatus status,
        int deferredCount,
        int sortOrder,
        Instant completedAt,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}

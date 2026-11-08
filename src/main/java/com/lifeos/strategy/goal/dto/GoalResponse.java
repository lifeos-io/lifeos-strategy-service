package com.lifeos.strategy.goal.dto;

import com.lifeos.strategy.goal.entity.GoalDomain;
import com.lifeos.strategy.goal.entity.GoalStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GoalResponse(
        UUID id,
        String title,
        GoalDomain domain,
        String whyItMatters,
        String successDefinition,
        LocalDate windowStart,
        LocalDate windowEnd,
        GoalStatus status,
        int priority,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}

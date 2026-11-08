package com.lifeos.strategy.goal.event;

import com.lifeos.strategy.goal.entity.GoalDomain;
import com.lifeos.strategy.goal.entity.GoalStatus;
import java.time.LocalDate;
import java.util.UUID;

/** Payload of {@code GoalChanged} v1 (lifeos-contracts: schemas/strategy/GoalChanged.v1.schema.json). */
public record GoalChangedV1(
        UUID goalId,
        String title,
        GoalDomain domain,
        String whyItMatters,
        String successDefinition,
        LocalDate windowStart,
        LocalDate windowEnd,
        GoalStatus status,
        int priority,
        int version) {

    public static final String TYPE = "GoalChanged";
    public static final int VERSION = 1;
}

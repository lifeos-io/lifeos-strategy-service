package com.lifeos.strategy.weeklyplan.event;

import java.util.UUID;

/** Payload of {@code CapacityChanged} v1 (lifeos-contracts: schemas/strategy/CapacityChanged.v1.schema.json). */
public record CapacityChangedV1(
        UUID weeklyPlanId,
        String isoWeek,
        int previousCapacityMinutes,
        int capacityMinutes) {

    public static final String TYPE = "CapacityChanged";
    public static final int VERSION = 1;
}

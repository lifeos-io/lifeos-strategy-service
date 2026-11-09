package com.lifeos.strategy.decision.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload of Trigger's {@code ReopenTriggerFired} v1, consumed from {@code lifeos.trigger.events.v1}
 * (lifeos-contracts: schemas/trigger/ReopenTriggerFired.v1.schema.json).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReopenTriggerFiredV1(UUID decisionId, int position, String description, LocalDate firesOn,
        Instant dueAt) {

    public static final String TYPE = "ReopenTriggerFired";
    public static final int VERSION = 1;
}

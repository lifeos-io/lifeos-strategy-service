package com.lifeos.strategy.evidence.event;

import com.lifeos.strategy.evidence.entity.EvidenceLevel;
import com.lifeos.strategy.evidence.entity.EvidenceStrength;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Payload of {@code EvidenceRecorded} v1 (lifeos-contracts: schemas/strategy/EvidenceRecorded.v1.schema.json). */
public record EvidenceRecordedV1(
        UUID evidenceId,
        String title,
        EvidenceLevel level,
        EvidenceStrength strength,
        LocalDate occurredOn,
        UUID parentEvidenceId,
        UUID actionId,
        UUID feedbackId,
        List<UUID> goalIds,
        List<UUID> phaseKeys) {

    public static final String TYPE = "EvidenceRecorded";
    public static final int VERSION = 1;
}

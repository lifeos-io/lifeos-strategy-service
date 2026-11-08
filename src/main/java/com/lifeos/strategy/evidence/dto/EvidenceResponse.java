package com.lifeos.strategy.evidence.dto;

import com.lifeos.strategy.evidence.entity.EvidenceLevel;
import com.lifeos.strategy.evidence.entity.EvidenceStrength;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EvidenceResponse(
        UUID id,
        String title,
        EvidenceLevel level,
        EvidenceStrength strength,
        LocalDate occurredOn,
        UUID parentEvidenceId,
        UUID actionId,
        UUID feedbackId,
        String note,
        List<UUID> goalIds,
        List<UUID> phaseKeys,
        Instant createdAt) {
}

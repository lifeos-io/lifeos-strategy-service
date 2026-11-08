package com.lifeos.strategy.evidence.service;

import com.lifeos.strategy.evidence.dto.EvidenceResponse;
import com.lifeos.strategy.evidence.dto.RecordEvidenceRequest;
import com.lifeos.strategy.evidence.entity.EvidenceLevel;
import java.util.List;
import java.util.UUID;

/**
 * Evidence of progress; append-only. Every link (parent, action, feedback, goals, phases) must be the user's
 * own; a parent must be a lower step of the chain. Recording publishes {@code EvidenceRecorded}.
 */
public interface EvidenceService {

    EvidenceResponse record(UUID userId, RecordEvidenceRequest request);

    /** Most recent first; {@code null} filters match everything. */
    List<EvidenceResponse> list(UUID userId, UUID goalId, EvidenceLevel level);

    EvidenceResponse get(UUID userId, UUID evidenceId);
}

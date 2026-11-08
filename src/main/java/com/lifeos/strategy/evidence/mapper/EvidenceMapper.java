package com.lifeos.strategy.evidence.mapper;

import com.lifeos.strategy.evidence.dto.EvidenceResponse;
import com.lifeos.strategy.evidence.entity.Evidence;
import com.lifeos.strategy.evidence.event.EvidenceRecordedV1;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EvidenceMapper {

    public EvidenceResponse toResponse(Evidence evidence) {
        return new EvidenceResponse(evidence.getId(), evidence.getTitle(), evidence.getLevel(),
                evidence.getStrength(), evidence.getOccurredOn(), evidence.getParentEvidenceId(),
                evidence.getActionId(), evidence.getFeedbackId(), evidence.getNote(), sorted(evidence.getGoalIds()),
                sorted(evidence.getPhaseKeys()), evidence.getCreatedAt());
    }

    /** The note stays in Strategy: it is the user's private context, not something other services act on. */
    public EvidenceRecordedV1 toRecordedEvent(Evidence evidence) {
        return new EvidenceRecordedV1(evidence.getId(), evidence.getTitle(), evidence.getLevel(),
                evidence.getStrength(), evidence.getOccurredOn(), evidence.getParentEvidenceId(),
                evidence.getActionId(), evidence.getFeedbackId(), sorted(evidence.getGoalIds()),
                sorted(evidence.getPhaseKeys()));
    }

    private static List<UUID> sorted(Collection<UUID> ids) {
        return ids.stream().sorted().toList();
    }
}

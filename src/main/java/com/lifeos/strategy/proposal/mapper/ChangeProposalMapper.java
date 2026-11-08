package com.lifeos.strategy.proposal.mapper;

import com.lifeos.strategy.proposal.dto.ChangeProposalResponse;
import com.lifeos.strategy.proposal.dto.ChangeProposalResponse.PhaseResponse;
import com.lifeos.strategy.proposal.dto.ChangeProposalResponse.RevisionResponse;
import com.lifeos.strategy.proposal.dto.ChangeProposalResponse.SourceResponse;
import com.lifeos.strategy.proposal.entity.ChangeProposal;
import com.lifeos.strategy.proposal.event.ProposalDecidedV1;
import com.lifeos.strategy.proposal.event.RecommendationProposedV1.RevisionV1;
import com.lifeos.strategy.strategyversion.dto.CreateStrategyVersionRequest;
import com.lifeos.strategy.strategyversion.dto.CreateStrategyVersionRequest.PhaseRequest;
import java.util.Comparator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Also converts the stored revision (JSON) to and from {@link RevisionV1}. */
@Component
public class ChangeProposalMapper {

    private final JsonMapper jsonMapper;

    public ChangeProposalMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public ChangeProposalResponse toResponse(ChangeProposal proposal, boolean applicable) {
        RevisionV1 revision = readRevision(proposal);
        return new ChangeProposalResponse(proposal.getId(), proposal.getRecommendationId(), proposal.getKind(),
                proposal.getGoalId(), proposal.getBasedOnVersionNo(), proposal.getRationale(),
                proposal.getSources().stream()
                        .map(s -> new SourceResponse(s.getType(), s.getId()))
                        .sorted(Comparator.comparing(SourceResponse::type).thenComparing(SourceResponse::id))
                        .toList(),
                new RevisionResponse(revision.summary(), revision.changeReason(),
                        revision.phases().stream()
                                .map(p -> new PhaseResponse(p.phaseKey(), p.title(), p.startsOn(), p.endsOn(),
                                        p.exitCriteria(), p.status()))
                                .toList(),
                        revision.effortAllocation()),
                proposal.getStatus(), applicable, proposal.getDecisionNote(), proposal.getDecidedAt(),
                proposal.getAppliedStrategyVersionId(), proposal.getCreatedAt());
    }

    public ProposalDecidedV1 toDecidedEvent(ChangeProposal proposal) {
        return new ProposalDecidedV1(proposal.getId(), proposal.getRecommendationId(), proposal.getGoalId(),
                proposal.getStatus(), proposal.getDecisionNote(), proposal.getAppliedStrategyVersionId(),
                proposal.getDecidedAt());
    }

    /** The revision as a strategy version request, expecting the version Coach based it on. */
    public CreateStrategyVersionRequest toStrategyVersionRequest(RevisionV1 revision, Integer basedOnVersionNo) {
        return new CreateStrategyVersionRequest(basedOnVersionNo, revision.summary(), revision.changeReason(),
                revision.phases() == null ? null : revision.phases().stream()
                        .map(p -> new PhaseRequest(p.phaseKey(), p.title(), p.startsOn(), p.endsOn(),
                                p.exitCriteria(), p.status()))
                        .toList(),
                revision.effortAllocation());
    }

    public String writeRevision(RevisionV1 revision) {
        return jsonMapper.writeValueAsString(revision);
    }

    public RevisionV1 readRevision(ChangeProposal proposal) {
        return jsonMapper.readValue(proposal.getRevision(), RevisionV1.class);
    }
}

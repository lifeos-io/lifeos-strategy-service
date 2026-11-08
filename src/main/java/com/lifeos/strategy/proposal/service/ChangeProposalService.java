package com.lifeos.strategy.proposal.service;

import com.lifeos.strategy.proposal.dto.ChangeProposalResponse;
import com.lifeos.strategy.proposal.dto.RejectProposalRequest;
import com.lifeos.strategy.proposal.entity.ProposalStatus;
import com.lifeos.strategy.proposal.event.RecommendationProposedV1;
import java.util.List;
import java.util.UUID;

/**
 * Changes Coach proposes. They arrive as {@code RecommendationProposed} events and wait for the user; only an
 * accepted proposal changes the plan. Every decision (also a supersession) publishes {@code ProposalDecided}.
 */
public interface ChangeProposalService {

    /**
     * Stores a recommendation as a PENDING proposal, superseding the goal's pending one. A repeated
     * recommendation, or one for a goal the user no longer has open, is skipped.
     *
     * @throws com.lifeos.base.messaging.MalformedEventException when the revision is not a valid strategy version
     */
    void receive(UUID userId, RecommendationProposedV1 recommendation);

    /** Newest first; {@code null} filters match everything. */
    List<ChangeProposalResponse> list(UUID userId, ProposalStatus status, UUID goalId);

    ChangeProposalResponse get(UUID userId, UUID proposalId);

    /** PENDING → ACCEPTED: applies the revision as the goal's new strategy version. */
    ChangeProposalResponse accept(UUID userId, UUID proposalId);

    /** PENDING → REJECTED. */
    ChangeProposalResponse reject(UUID userId, UUID proposalId, RejectProposalRequest request);
}

package com.lifeos.strategy.proposal.event;

import com.lifeos.strategy.proposal.entity.ProposalStatus;
import java.time.Instant;
import java.util.UUID;

/** Payload of {@code ProposalDecided} v1 (lifeos-contracts: schemas/strategy/ProposalDecided.v1.schema.json). */
public record ProposalDecidedV1(
        UUID proposalId,
        UUID recommendationId,
        UUID goalId,
        ProposalStatus decision,
        String note,
        UUID strategyVersionId,
        Instant decidedAt) {

    public static final String TYPE = "ProposalDecided";
    public static final int VERSION = 1;
}

package com.lifeos.strategy.proposal.entity;

/**
 * PENDING (waits for the user) → ACCEPTED (applied) or REJECTED; a newer proposal for the same goal turns a
 * pending one SUPERSEDED. All but PENDING are final.
 */
public enum ProposalStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    SUPERSEDED
}

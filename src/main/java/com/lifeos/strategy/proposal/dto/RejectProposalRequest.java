package com.lifeos.strategy.proposal.dto;

import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/strategy/proposals/{proposalId}/reject}; the body is optional.
 *
 * @param note why the user rejects it; reported back to Coach
 */
public record RejectProposalRequest(@Size(max = 1000) String note) {
}

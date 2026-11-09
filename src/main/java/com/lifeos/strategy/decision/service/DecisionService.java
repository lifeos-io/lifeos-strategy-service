package com.lifeos.strategy.decision.service;

import com.lifeos.strategy.decision.dto.CloseDecisionRequest;
import com.lifeos.strategy.decision.dto.DecideRequest;
import com.lifeos.strategy.decision.dto.DecisionDraftRequest;
import com.lifeos.strategy.decision.dto.DecisionResponse;
import com.lifeos.strategy.decision.dto.ReopenDecisionRequest;
import com.lifeos.strategy.decision.entity.DecisionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Long-term decisions: drafted, decided ({@code DecisionRecorded}) and looked back on ({@code DecisionClosed}).
 * Only drafts change; a decided decision is history. Reopening one starts a new draft from it
 * ({@code DecisionReopened}); deciding that draft supersedes an open original ({@code DecisionSuperseded}). A
 * decision whose assumption was falsified is flagged ({@code falsifiedAssumptionIds},
 * {@code DecisionAssumptionFalsified}).
 */
public interface DecisionService {

    DecisionResponse create(UUID userId, DecisionDraftRequest request);

    /** Replaces a draft's content. */
    DecisionResponse revise(UUID userId, UUID decisionId, DecisionDraftRequest request);

    DecisionResponse decide(UUID userId, UUID decisionId, DecideRequest request);

    DecisionResponse close(UUID userId, UUID decisionId, CloseDecisionRequest request);

    /** A new draft that weighs a decided (OPEN or CLOSED) decision again; the original is not changed. */
    DecisionResponse reopen(UUID userId, UUID decisionId, ReopenDecisionRequest request);

    /**
     * Newest first; {@code null} filters match everything.
     *
     * @param needsReview only OPEN decisions that rest on a FALSIFIED assumption
     */
    List<DecisionResponse> list(UUID userId, DecisionStatus status, UUID goalId, boolean needsReview);

    DecisionResponse get(UUID userId, UUID decisionId);

    /**
     * Called when one of the user's knowledge items became FALSIFIED: publishes
     * {@code DecisionAssumptionFalsified} for each OPEN decision that rests on it.
     */
    void assumptionFalsified(UUID userId, UUID knowledgeItemId, String statement, Instant falsifiedAt);

    /**
     * Trigger fired the decision's reopen trigger at {@code position} ({@code ReopenTriggerFired}): records it on
     * the trigger. A decision that is gone or no longer OPEN, or a trigger already fired, is left as it is.
     */
    void reopenTriggerFired(UUID userId, UUID decisionId, int position, Instant firedAt);
}

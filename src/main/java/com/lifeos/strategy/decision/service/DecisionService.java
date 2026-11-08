package com.lifeos.strategy.decision.service;

import com.lifeos.strategy.decision.dto.CloseDecisionRequest;
import com.lifeos.strategy.decision.dto.DecideRequest;
import com.lifeos.strategy.decision.dto.DecisionDraftRequest;
import com.lifeos.strategy.decision.dto.DecisionResponse;
import com.lifeos.strategy.decision.entity.DecisionStatus;
import java.util.List;
import java.util.UUID;

/**
 * Long-term decisions: drafted, decided ({@code DecisionRecorded}) and looked back on ({@code DecisionClosed}).
 * Only drafts change; a decided decision is history.
 */
public interface DecisionService {

    DecisionResponse create(UUID userId, DecisionDraftRequest request);

    /** Replaces a draft's content. */
    DecisionResponse revise(UUID userId, UUID decisionId, DecisionDraftRequest request);

    DecisionResponse decide(UUID userId, UUID decisionId, DecideRequest request);

    DecisionResponse close(UUID userId, UUID decisionId, CloseDecisionRequest request);

    /** Newest first; {@code null} filters match everything. */
    List<DecisionResponse> list(UUID userId, DecisionStatus status, UUID goalId);

    DecisionResponse get(UUID userId, UUID decisionId);
}

package com.lifeos.strategy.idea.service;

import com.lifeos.strategy.idea.dto.CaptureIdeaRequest;
import com.lifeos.strategy.idea.dto.EvaluateIdeaRequest;
import com.lifeos.strategy.idea.dto.IdeaResponse;
import com.lifeos.strategy.idea.dto.PromoteIdeaRequest;
import com.lifeos.strategy.idea.dto.UpdateIdeaRequest;
import com.lifeos.strategy.idea.entity.IdeaStatus;
import java.util.List;
import java.util.UUID;

/**
 * The user's ideas: captured into the INBOX, evaluated, and at most two ACTIVE at a time. Every creation and every
 * real change publishes {@code IdeaChanged} with the idea's full state.
 */
public interface IdeaService {

    IdeaResponse capture(UUID userId, CaptureIdeaRequest request);

    /** Newest first; {@code null} filters match everything. */
    List<IdeaResponse> list(UUID userId, IdeaStatus status, UUID goalId);

    IdeaResponse get(UUID userId, UUID ideaId);

    IdeaResponse update(UUID userId, UUID ideaId, UpdateIdeaRequest request);

    IdeaResponse evaluate(UUID userId, UUID ideaId, EvaluateIdeaRequest request);

    /** INBOX → ACTIVE: needs a latest PURSUE verdict and a free slot (at most two active ideas). */
    IdeaResponse activate(UUID userId, UUID ideaId);

    /** INBOX or ACTIVE → SHELVED. */
    IdeaResponse shelve(UUID userId, UUID ideaId);

    /** SHELVED → INBOX. */
    IdeaResponse reopen(UUID userId, UUID ideaId);

    /** INBOX, ACTIVE or SHELVED → DROPPED. */
    IdeaResponse drop(UUID userId, UUID ideaId);

    /** ACTIVE → PROMOTED into one of the user's open goals. */
    IdeaResponse promote(UUID userId, UUID ideaId, PromoteIdeaRequest request);
}

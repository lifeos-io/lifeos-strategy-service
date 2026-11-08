package com.lifeos.strategy.strategyversion.service;

import com.lifeos.strategy.strategyversion.dto.CreateStrategyVersionRequest;
import com.lifeos.strategy.strategyversion.dto.StrategyVersionResponse;
import java.util.List;
import java.util.UUID;

/**
 * A goal's strategy and its history. Every new version (the first one or a revision) becomes the current one
 * and publishes {@code StrategyRevised} with its full content.
 */
public interface StrategyVersionService {

    StrategyVersionResponse create(UUID userId, UUID goalId, CreateStrategyVersionRequest request);

    /**
     * {@link #create} for an accepted change proposal: the new version records {@code sourceProposalId}, and the
     * same rules apply (the goal is locked, {@code expectedCurrentVersionNo} must match, phases and effort are
     * validated).
     */
    StrategyVersionResponse create(UUID userId, UUID goalId, CreateStrategyVersionRequest request,
            UUID sourceProposalId);

    StrategyVersionResponse getCurrent(UUID userId, UUID goalId);

    StrategyVersionResponse get(UUID userId, UUID goalId, int versionNo);

    /** Newest first. */
    List<StrategyVersionResponse> list(UUID userId, UUID goalId);
}

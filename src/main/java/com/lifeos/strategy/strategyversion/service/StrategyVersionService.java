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

    StrategyVersionResponse getCurrent(UUID userId, UUID goalId);

    StrategyVersionResponse get(UUID userId, UUID goalId, int versionNo);

    /** Newest first. */
    List<StrategyVersionResponse> list(UUID userId, UUID goalId);
}

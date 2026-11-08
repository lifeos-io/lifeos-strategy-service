package com.lifeos.strategy.strategyversion.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.strategy.strategyversion.dto.CreateStrategyVersionRequest;
import com.lifeos.strategy.strategyversion.dto.StrategyVersionResponse;
import com.lifeos.strategy.strategyversion.service.StrategyVersionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/strategy/goals/{goalId}/strategy-versions}: a goal's strategy and how it evolved. */
@RestController
@RequestMapping("/api/strategy/goals/{goalId}/strategy-versions")
public class StrategyVersionController {

    private final StrategyVersionService strategyVersionService;

    public StrategyVersionController(StrategyVersionService strategyVersionService) {
        this.strategyVersionService = strategyVersionService;
    }

    /** The first strategy or a revision; the new version becomes the current one. */
    @PostMapping
    public ResponseEntity<StrategyVersionResponse> create(@CurrentUser UUID userId, @PathVariable UUID goalId,
            @Valid @RequestBody CreateStrategyVersionRequest request) {
        StrategyVersionResponse version = strategyVersionService.create(userId, goalId, request);
        URI location = URI.create("/api/strategy/goals/" + goalId + "/strategy-versions/" + version.versionNo());
        return ResponseEntity.created(location).body(version);
    }

    @GetMapping("/current")
    public StrategyVersionResponse getCurrent(@CurrentUser UUID userId, @PathVariable UUID goalId) {
        return strategyVersionService.getCurrent(userId, goalId);
    }

    @GetMapping("/{versionNo}")
    public StrategyVersionResponse get(@CurrentUser UUID userId, @PathVariable UUID goalId,
            @PathVariable int versionNo) {
        return strategyVersionService.get(userId, goalId, versionNo);
    }

    /** Newest first. */
    @GetMapping
    public List<StrategyVersionResponse> list(@CurrentUser UUID userId, @PathVariable UUID goalId) {
        return strategyVersionService.list(userId, goalId);
    }
}

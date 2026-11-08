package com.lifeos.strategy.decision.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.strategy.decision.dto.CloseDecisionRequest;
import com.lifeos.strategy.decision.dto.DecideRequest;
import com.lifeos.strategy.decision.dto.DecisionDraftRequest;
import com.lifeos.strategy.decision.dto.DecisionResponse;
import com.lifeos.strategy.decision.entity.DecisionStatus;
import com.lifeos.strategy.decision.service.DecisionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/strategy/decisions}: the user's long-term decisions. */
@RestController
@RequestMapping("/api/strategy/decisions")
public class DecisionController {

    private final DecisionService decisionService;

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    /** A new draft. */
    @PostMapping
    public ResponseEntity<DecisionResponse> create(@CurrentUser UUID userId,
            @Valid @RequestBody DecisionDraftRequest request) {
        DecisionResponse decision = decisionService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/strategy/decisions/" + decision.id())).body(decision);
    }

    /** Newest first; {@code ?status=OPEN&goalId=...} narrows the list. */
    @GetMapping
    public List<DecisionResponse> list(@CurrentUser UUID userId,
            @RequestParam(required = false) DecisionStatus status, @RequestParam(required = false) UUID goalId) {
        return decisionService.list(userId, status, goalId);
    }

    @GetMapping("/{decisionId}")
    public DecisionResponse get(@CurrentUser UUID userId, @PathVariable UUID decisionId) {
        return decisionService.get(userId, decisionId);
    }

    /** Replaces a draft's content; {@code version} is required. */
    @PutMapping("/{decisionId}")
    public DecisionResponse revise(@CurrentUser UUID userId, @PathVariable UUID decisionId,
            @Valid @RequestBody DecisionDraftRequest request) {
        return decisionService.revise(userId, decisionId, request);
    }

    /** DRAFT → OPEN: commits to one alternative. */
    @PostMapping("/{decisionId}/decide")
    public DecisionResponse decide(@CurrentUser UUID userId, @PathVariable UUID decisionId,
            @Valid @RequestBody DecideRequest request) {
        return decisionService.decide(userId, decisionId, request);
    }

    /** OPEN → CLOSED: the look-back. */
    @PostMapping("/{decisionId}/close")
    public DecisionResponse close(@CurrentUser UUID userId, @PathVariable UUID decisionId,
            @Valid @RequestBody CloseDecisionRequest request) {
        return decisionService.close(userId, decisionId, request);
    }
}

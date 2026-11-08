package com.lifeos.strategy.idea.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.strategy.idea.dto.CaptureIdeaRequest;
import com.lifeos.strategy.idea.dto.EvaluateIdeaRequest;
import com.lifeos.strategy.idea.dto.IdeaResponse;
import com.lifeos.strategy.idea.dto.PromoteIdeaRequest;
import com.lifeos.strategy.idea.dto.UpdateIdeaRequest;
import com.lifeos.strategy.idea.entity.IdeaStatus;
import com.lifeos.strategy.idea.service.IdeaService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/strategy/ideas}: ideas captured, evaluated, and at most two active at a time. */
@RestController
@RequestMapping("/api/strategy/ideas")
public class IdeaController {

    private final IdeaService ideaService;

    public IdeaController(IdeaService ideaService) {
        this.ideaService = ideaService;
    }

    /** Captures an idea into the INBOX. */
    @PostMapping
    public ResponseEntity<IdeaResponse> capture(@CurrentUser UUID userId,
            @Valid @RequestBody CaptureIdeaRequest request) {
        IdeaResponse idea = ideaService.capture(userId, request);
        return ResponseEntity.created(URI.create("/api/strategy/ideas/" + idea.id())).body(idea);
    }

    /** Newest first; {@code ?status=ACTIVE&goalId=...} narrows the list. */
    @GetMapping
    public List<IdeaResponse> list(@CurrentUser UUID userId, @RequestParam(required = false) IdeaStatus status,
            @RequestParam(required = false) UUID goalId) {
        return ideaService.list(userId, status, goalId);
    }

    @GetMapping("/{ideaId}")
    public IdeaResponse get(@CurrentUser UUID userId, @PathVariable UUID ideaId) {
        return ideaService.get(userId, ideaId);
    }

    @PatchMapping("/{ideaId}")
    public IdeaResponse update(@CurrentUser UUID userId, @PathVariable UUID ideaId,
            @Valid @RequestBody UpdateIdeaRequest request) {
        return ideaService.update(userId, ideaId, request);
    }

    /** Appends an evaluation; the latest one decides whether the idea may become active. */
    @PostMapping("/{ideaId}/evaluations")
    public IdeaResponse evaluate(@CurrentUser UUID userId, @PathVariable UUID ideaId,
            @Valid @RequestBody EvaluateIdeaRequest request) {
        return ideaService.evaluate(userId, ideaId, request);
    }

    /** INBOX → ACTIVE (latest verdict PURSUE, at most two active ideas). */
    @PostMapping("/{ideaId}/activate")
    public IdeaResponse activate(@CurrentUser UUID userId, @PathVariable UUID ideaId) {
        return ideaService.activate(userId, ideaId);
    }

    /** INBOX or ACTIVE → SHELVED. */
    @PostMapping("/{ideaId}/shelve")
    public IdeaResponse shelve(@CurrentUser UUID userId, @PathVariable UUID ideaId) {
        return ideaService.shelve(userId, ideaId);
    }

    /** SHELVED → INBOX. */
    @PostMapping("/{ideaId}/reopen")
    public IdeaResponse reopen(@CurrentUser UUID userId, @PathVariable UUID ideaId) {
        return ideaService.reopen(userId, ideaId);
    }

    /** INBOX, ACTIVE or SHELVED → DROPPED (final). */
    @PostMapping("/{ideaId}/drop")
    public IdeaResponse drop(@CurrentUser UUID userId, @PathVariable UUID ideaId) {
        return ideaService.drop(userId, ideaId);
    }

    /** ACTIVE → PROMOTED (final): the idea grew into one of the user's goals. */
    @PostMapping("/{ideaId}/promote")
    public IdeaResponse promote(@CurrentUser UUID userId, @PathVariable UUID ideaId,
            @Valid @RequestBody PromoteIdeaRequest request) {
        return ideaService.promote(userId, ideaId, request);
    }
}

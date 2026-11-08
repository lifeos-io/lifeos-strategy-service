package com.lifeos.strategy.knowledge.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.strategy.knowledge.dto.CreateKnowledgeItemRequest;
import com.lifeos.strategy.knowledge.dto.KnowledgeItemResponse;
import com.lifeos.strategy.knowledge.dto.UpdateKnowledgeItemRequest;
import com.lifeos.strategy.knowledge.entity.KnowledgeKind;
import com.lifeos.strategy.knowledge.entity.KnowledgeStatus;
import com.lifeos.strategy.knowledge.service.KnowledgeItemService;
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

/** {@code /api/strategy/goals/{goalId}/knowledge-items}: the goal's knowledge map. */
@RestController
@RequestMapping("/api/strategy/goals/{goalId}/knowledge-items")
public class KnowledgeItemController {

    private final KnowledgeItemService knowledgeItemService;

    public KnowledgeItemController(KnowledgeItemService knowledgeItemService) {
        this.knowledgeItemService = knowledgeItemService;
    }

    @PostMapping
    public ResponseEntity<KnowledgeItemResponse> create(@CurrentUser UUID userId, @PathVariable UUID goalId,
            @Valid @RequestBody CreateKnowledgeItemRequest request) {
        KnowledgeItemResponse item = knowledgeItemService.create(userId, goalId, request);
        URI location = URI.create("/api/strategy/goals/" + goalId + "/knowledge-items/" + item.id());
        return ResponseEntity.created(location).body(item);
    }

    /** Oldest first; {@code ?kind=ASSUMPTION&status=UNVERIFIED} narrows the list. */
    @GetMapping
    public List<KnowledgeItemResponse> list(@CurrentUser UUID userId, @PathVariable UUID goalId,
            @RequestParam(required = false) KnowledgeKind kind,
            @RequestParam(required = false) KnowledgeStatus status) {
        return knowledgeItemService.list(userId, goalId, kind, status);
    }

    @GetMapping("/{itemId}")
    public KnowledgeItemResponse get(@CurrentUser UUID userId, @PathVariable UUID goalId,
            @PathVariable UUID itemId) {
        return knowledgeItemService.get(userId, goalId, itemId);
    }

    @PatchMapping("/{itemId}")
    public KnowledgeItemResponse update(@CurrentUser UUID userId, @PathVariable UUID goalId,
            @PathVariable UUID itemId, @Valid @RequestBody UpdateKnowledgeItemRequest request) {
        return knowledgeItemService.update(userId, goalId, itemId, request);
    }
}

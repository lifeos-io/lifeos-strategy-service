package com.lifeos.strategy.evidence.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.strategy.evidence.dto.EvidenceResponse;
import com.lifeos.strategy.evidence.dto.RecordEvidenceRequest;
import com.lifeos.strategy.evidence.entity.EvidenceLevel;
import com.lifeos.strategy.evidence.service.EvidenceService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/strategy/evidence}: evidence of real-life progress (append-only). */
@RestController
@RequestMapping("/api/strategy/evidence")
public class EvidenceController {

    private final EvidenceService evidenceService;

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @PostMapping
    public ResponseEntity<EvidenceResponse> record(@CurrentUser UUID userId,
            @Valid @RequestBody RecordEvidenceRequest request) {
        EvidenceResponse evidence = evidenceService.record(userId, request);
        return ResponseEntity.created(URI.create("/api/strategy/evidence/" + evidence.id())).body(evidence);
    }

    /** Most recent first; {@code ?goalId=...&level=OUTCOME} narrows the list. */
    @GetMapping
    public List<EvidenceResponse> list(@CurrentUser UUID userId, @RequestParam(required = false) UUID goalId,
            @RequestParam(required = false) EvidenceLevel level) {
        return evidenceService.list(userId, goalId, level);
    }

    @GetMapping("/{evidenceId}")
    public EvidenceResponse get(@CurrentUser UUID userId, @PathVariable UUID evidenceId) {
        return evidenceService.get(userId, evidenceId);
    }
}

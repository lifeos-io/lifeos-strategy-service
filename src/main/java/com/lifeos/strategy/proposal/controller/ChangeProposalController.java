package com.lifeos.strategy.proposal.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.strategy.proposal.dto.ChangeProposalResponse;
import com.lifeos.strategy.proposal.dto.RejectProposalRequest;
import com.lifeos.strategy.proposal.entity.ProposalStatus;
import com.lifeos.strategy.proposal.service.ChangeProposalService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/strategy/proposals}: changes Coach proposes, waiting for the user's decision. */
@RestController
@RequestMapping("/api/strategy/proposals")
public class ChangeProposalController {

    private final ChangeProposalService changeProposalService;

    public ChangeProposalController(ChangeProposalService changeProposalService) {
        this.changeProposalService = changeProposalService;
    }

    /** Newest first; {@code ?status=PENDING&goalId=...} narrows the list. */
    @GetMapping
    public List<ChangeProposalResponse> list(@CurrentUser UUID userId,
            @RequestParam(required = false) ProposalStatus status, @RequestParam(required = false) UUID goalId) {
        return changeProposalService.list(userId, status, goalId);
    }

    @GetMapping("/{proposalId}")
    public ChangeProposalResponse get(@CurrentUser UUID userId, @PathVariable UUID proposalId) {
        return changeProposalService.get(userId, proposalId);
    }

    /** PENDING → ACCEPTED: the revision becomes the goal's new strategy version. */
    @PostMapping("/{proposalId}/accept")
    public ChangeProposalResponse accept(@CurrentUser UUID userId, @PathVariable UUID proposalId) {
        return changeProposalService.accept(userId, proposalId);
    }

    /** PENDING → REJECTED; an optional {@code note} says why. */
    @PostMapping("/{proposalId}/reject")
    public ChangeProposalResponse reject(@CurrentUser UUID userId, @PathVariable UUID proposalId,
            @Valid @RequestBody(required = false) RejectProposalRequest request) {
        return changeProposalService.reject(userId, proposalId, request);
    }
}

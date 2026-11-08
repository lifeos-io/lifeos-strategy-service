package com.lifeos.strategy.feedback.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.strategy.feedback.dto.FeedbackResponse;
import com.lifeos.strategy.feedback.dto.RecordFeedbackRequest;
import com.lifeos.strategy.feedback.service.FeedbackService;
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

/** {@code /api/strategy/feedback}: feedback the user received (append-only). */
@RestController
@RequestMapping("/api/strategy/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<FeedbackResponse> record(@CurrentUser UUID userId,
            @Valid @RequestBody RecordFeedbackRequest request) {
        FeedbackResponse feedback = feedbackService.record(userId, request);
        return ResponseEntity.created(URI.create("/api/strategy/feedback/" + feedback.id())).body(feedback);
    }

    /** Most recently received first. */
    @GetMapping
    public List<FeedbackResponse> list(@CurrentUser UUID userId) {
        return feedbackService.list(userId);
    }

    @GetMapping("/{feedbackId}")
    public FeedbackResponse get(@CurrentUser UUID userId, @PathVariable UUID feedbackId) {
        return feedbackService.get(userId, feedbackId);
    }
}

package com.lifeos.strategy.weeklyreview.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.strategy.weeklyreview.dto.CompleteWeeklyReviewRequest;
import com.lifeos.strategy.weeklyreview.dto.WeeklyReviewPreviewResponse;
import com.lifeos.strategy.weeklyreview.dto.WeeklyReviewResponse;
import com.lifeos.strategy.weeklyreview.service.WeeklyReviewService;
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

/**
 * {@code /api/strategy/weekly-plans/{isoWeek}/review}: the look-back that closes a week, and
 * {@code /api/strategy/weekly-reviews}: all of them.
 */
@RestController
@RequestMapping("/api/strategy")
public class WeeklyReviewController {

    private final WeeklyReviewService weeklyReviewService;

    public WeeklyReviewController(WeeklyReviewService weeklyReviewService) {
        this.weeklyReviewService = weeklyReviewService;
    }

    /** The active week's numbers as they are now, for the review screen; nothing is stored. */
    @GetMapping("/weekly-plans/{isoWeek}/review/preview")
    public WeeklyReviewPreviewResponse preview(@CurrentUser UUID userId, @PathVariable String isoWeek) {
        return weeklyReviewService.preview(userId, isoWeek);
    }

    /** Writes the review and closes the week (ACTIVE → CLOSED). */
    @PostMapping("/weekly-plans/{isoWeek}/review")
    public ResponseEntity<WeeklyReviewResponse> complete(@CurrentUser UUID userId, @PathVariable String isoWeek,
            @Valid @RequestBody CompleteWeeklyReviewRequest request) {
        WeeklyReviewResponse review = weeklyReviewService.complete(userId, isoWeek, request);
        return ResponseEntity.created(URI.create("/api/strategy/weekly-plans/" + isoWeek + "/review")).body(review);
    }

    @GetMapping("/weekly-plans/{isoWeek}/review")
    public WeeklyReviewResponse get(@CurrentUser UUID userId, @PathVariable String isoWeek) {
        return weeklyReviewService.get(userId, isoWeek);
    }

    /** Newest week first. */
    @GetMapping("/weekly-reviews")
    public List<WeeklyReviewResponse> list(@CurrentUser UUID userId) {
        return weeklyReviewService.list(userId);
    }
}

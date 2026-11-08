package com.lifeos.strategy.weeklyreview.service;

import com.lifeos.strategy.weeklyreview.dto.CompleteWeeklyReviewRequest;
import com.lifeos.strategy.weeklyreview.dto.WeeklyReviewPreviewResponse;
import com.lifeos.strategy.weeklyreview.dto.WeeklyReviewResponse;
import java.util.List;
import java.util.UUID;

/**
 * Weekly reviews: the look-back that closes the active week. A review is written once, together with closing the
 * week, and publishes {@code WeeklyReviewCompleted}.
 */
public interface WeeklyReviewService {

    /** The active week's numbers as they are now; nothing is stored. */
    WeeklyReviewPreviewResponse preview(UUID userId, String isoWeek);

    /** Counts the week, stores the review and closes the week (ACTIVE → CLOSED). */
    WeeklyReviewResponse complete(UUID userId, String isoWeek, CompleteWeeklyReviewRequest request);

    WeeklyReviewResponse get(UUID userId, String isoWeek);

    /** Newest week first. */
    List<WeeklyReviewResponse> list(UUID userId);
}

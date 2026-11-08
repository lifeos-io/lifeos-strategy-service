package com.lifeos.strategy.weeklyreview.dto;

import com.lifeos.strategy.weeklyreview.dto.WeeklyReviewResponse.ActionsResponse;
import com.lifeos.strategy.weeklyreview.dto.WeeklyReviewResponse.GoalOutcomeResponse;
import java.time.LocalDate;
import java.util.List;

/** The active week's numbers as they are now, for the review screen; nothing is stored. */
public record WeeklyReviewPreviewResponse(
        String isoWeek,
        LocalDate weekStart,
        int capacityMinutes,
        ActionsResponse actions,
        List<GoalOutcomeResponse> goals,
        int evidenceCount) {
}

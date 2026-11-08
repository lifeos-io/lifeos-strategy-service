package com.lifeos.strategy.weeklyreview.mapper;

import com.lifeos.strategy.weeklyreview.dto.WeeklyReviewPreviewResponse;
import com.lifeos.strategy.weeklyreview.dto.WeeklyReviewResponse;
import com.lifeos.strategy.weeklyreview.dto.WeeklyReviewResponse.ActionsResponse;
import com.lifeos.strategy.weeklyreview.dto.WeeklyReviewResponse.GoalOutcomeResponse;
import com.lifeos.strategy.weeklyreview.entity.ActionTally;
import com.lifeos.strategy.weeklyreview.entity.GoalOutcome;
import com.lifeos.strategy.weeklyreview.entity.WeeklyReview;
import com.lifeos.strategy.weeklyreview.event.WeeklyReviewCompletedV1;
import com.lifeos.strategy.weeklyreview.event.WeeklyReviewCompletedV1.ActionsV1;
import com.lifeos.strategy.weeklyreview.event.WeeklyReviewCompletedV1.GoalV1;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WeeklyReviewMapper {

    public WeeklyReviewResponse toResponse(WeeklyReview review) {
        return new WeeklyReviewResponse(review.getId(), review.getWeeklyPlanId(), review.getIsoWeek(),
                review.getCapacityMinutes(), toActionsResponse(review.getActions()),
                toGoalResponses(review.getGoals()), review.getEvidenceCount(), review.getEnergy(),
                review.getWentWell(), review.getGotStuck(), review.getLesson(), review.getNextWeekFocus(),
                review.getCompletedAt());
    }

    public WeeklyReviewPreviewResponse toPreviewResponse(String isoWeek, LocalDate weekStart, int capacityMinutes,
            ActionTally actions, List<GoalOutcome> goals, int evidenceCount) {
        return new WeeklyReviewPreviewResponse(isoWeek, weekStart, capacityMinutes, toActionsResponse(actions),
                toGoalResponses(goals), evidenceCount);
    }

    /** {@code weekStart} comes from the plan; the review does not store it. */
    public WeeklyReviewCompletedV1 toCompletedEvent(WeeklyReview review, LocalDate weekStart) {
        ActionTally actions = review.getActions();
        return new WeeklyReviewCompletedV1(review.getId(), review.getWeeklyPlanId(), review.getIsoWeek(), weekStart,
                review.getCapacityMinutes(),
                new ActionsV1(actions.getPlannedCount(), actions.getDoneCount(), actions.getDeferredCount(),
                        actions.getDroppedCount(), actions.getPlannedMinutes(), actions.getDoneMinutes()),
                review.getGoals().stream()
                        .map(g -> new GoalV1(g.getGoalId(), g.getAllocatedMinutes(), g.getDoneMinutes()))
                        .toList(),
                review.getEvidenceCount(), review.getEnergy(), review.getWentWell(), review.getGotStuck(),
                review.getLesson(), review.getNextWeekFocus(), review.getCompletedAt());
    }

    private static ActionsResponse toActionsResponse(ActionTally actions) {
        return new ActionsResponse(actions.getPlannedCount(), actions.getDoneCount(), actions.getDeferredCount(),
                actions.getDroppedCount(), actions.getPlannedMinutes(), actions.getDoneMinutes());
    }

    private static List<GoalOutcomeResponse> toGoalResponses(List<GoalOutcome> goals) {
        return goals.stream()
                .map(g -> new GoalOutcomeResponse(g.getGoalId(), g.getAllocatedMinutes(), g.getDoneMinutes()))
                .toList();
    }
}

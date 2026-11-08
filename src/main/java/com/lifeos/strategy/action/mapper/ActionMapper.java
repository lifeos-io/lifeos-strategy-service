package com.lifeos.strategy.action.mapper;

import com.lifeos.strategy.action.dto.ActionResponse;
import com.lifeos.strategy.action.entity.Action;
import com.lifeos.strategy.action.entity.ActionStatus;
import com.lifeos.strategy.action.event.ActionStatusChangedV1;
import com.lifeos.strategy.action.event.ActionStatusChangedV1.WeekV1;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ActionMapper {

    public ActionResponse toResponse(Action action) {
        return new ActionResponse(action.getId(), action.getWeeklyPlanId(), sorted(action.getGoalIds()),
                action.getPhaseKey(), action.getTitle(), action.getWhy(), action.getExpectedEvidence(),
                action.getKind(), action.getEstimateMinutes(), action.getStatus(), action.getDeferredCount(),
                action.getSortOrder(), action.getCompletedAt(), action.getVersion(), action.getCreatedAt(),
                action.getUpdatedAt());
    }

    /**
     * @param previousStatus {@code null} when the action was just added to the active week
     * @param weekActions    all actions of the week after the change
     */
    public ActionStatusChangedV1 toStatusChangedEvent(Action action, String isoWeek, ActionStatus previousStatus,
            List<Action> weekActions) {
        return new ActionStatusChangedV1(action.getId(), action.getWeeklyPlanId(), isoWeek,
                sorted(action.getGoalIds()), action.getPhaseKey(), action.getKind(), action.getEstimateMinutes(),
                previousStatus, action.getStatus(), action.getDeferredCount(), action.getCompletedAt(),
                toWeek(weekActions));
    }

    private static WeekV1 toWeek(List<Action> actions) {
        int[] byStatus = new int[ActionStatus.values().length];
        int plannedMinutes = 0;
        int doneMinutes = 0;
        for (Action action : actions) {
            byStatus[action.getStatus().ordinal()]++;
            if (action.countsAgainstCapacity()) {
                plannedMinutes += action.getEstimateMinutes();
            }
            if (action.getStatus() == ActionStatus.DONE) {
                doneMinutes += action.getEstimateMinutes();
            }
        }
        return new WeekV1(byStatus[ActionStatus.PLANNED.ordinal()], byStatus[ActionStatus.DONE.ordinal()],
                byStatus[ActionStatus.DEFERRED.ordinal()], byStatus[ActionStatus.DROPPED.ordinal()],
                plannedMinutes, doneMinutes);
    }

    private static List<UUID> sorted(Collection<UUID> ids) {
        return ids.stream().sorted().toList();
    }
}

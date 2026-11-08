package com.lifeos.strategy.weeklyplan.mapper;

import com.lifeos.strategy.weeklyplan.dto.WeeklyPlanResponse;
import com.lifeos.strategy.weeklyplan.dto.WeeklyPlanResponse.AllocationResponse;
import com.lifeos.strategy.weeklyplan.entity.WeeklyPlan;
import com.lifeos.strategy.weeklyplan.event.CapacityChangedV1;
import com.lifeos.strategy.weeklyplan.event.WeeklyPlanActivatedV1;
import com.lifeos.strategy.weeklyplan.event.WeeklyPlanActivatedV1.AllocationV1;
import org.springframework.stereotype.Component;

@Component
public class WeeklyPlanMapper {

    public WeeklyPlanResponse toResponse(WeeklyPlan plan) {
        return new WeeklyPlanResponse(plan.getId(), plan.getIsoWeek(), plan.getWeekStart(),
                plan.getCapacityMinutes(), plan.getAllocatedMinutes(), plan.getHeadline(), plan.getStatus(),
                plan.getActivatedAt(), plan.getClosedAt(), plan.getVersion(),
                plan.getAllocations().stream()
                        .map(a -> new AllocationResponse(a.getGoalId(), a.getStrategyVersionId(), a.getAllocatedMinutes()))
                        .toList(),
                plan.getCreatedAt(), plan.getUpdatedAt());
    }

    public WeeklyPlanActivatedV1 toActivatedEvent(WeeklyPlan plan, int plannedActionCount, int plannedActionMinutes) {
        return new WeeklyPlanActivatedV1(plan.getId(), plan.getIsoWeek(), plan.getWeekStart(),
                plan.getCapacityMinutes(), plan.getHeadline(),
                plan.getAllocations().stream()
                        .map(a -> new AllocationV1(a.getGoalId(), a.getStrategyVersionId(), a.getAllocatedMinutes()))
                        .toList(),
                plannedActionCount, plannedActionMinutes);
    }

    public CapacityChangedV1 toCapacityChangedEvent(WeeklyPlan plan, int previousCapacityMinutes) {
        return new CapacityChangedV1(plan.getId(), plan.getIsoWeek(), previousCapacityMinutes,
                plan.getCapacityMinutes());
    }
}

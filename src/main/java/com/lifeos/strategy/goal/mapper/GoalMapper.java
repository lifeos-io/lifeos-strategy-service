package com.lifeos.strategy.goal.mapper;

import com.lifeos.strategy.goal.dto.GoalResponse;
import com.lifeos.strategy.goal.entity.Goal;
import com.lifeos.strategy.goal.event.GoalChangedV1;
import org.springframework.stereotype.Component;

@Component
public class GoalMapper {

    public GoalResponse toResponse(Goal goal) {
        return new GoalResponse(goal.getId(), goal.getTitle(), goal.getDomain(), goal.getWhyItMatters(),
                goal.getSuccessDefinition(), goal.getWindowStart(), goal.getWindowEnd(), goal.getStatus(),
                goal.getPriority(), goal.getVersion(), goal.getCreatedAt(), goal.getUpdatedAt());
    }

    public GoalChangedV1 toChangedEvent(Goal goal) {
        return new GoalChangedV1(goal.getId(), goal.getTitle(), goal.getDomain(), goal.getWhyItMatters(),
                goal.getSuccessDefinition(), goal.getWindowStart(), goal.getWindowEnd(), goal.getStatus(),
                goal.getPriority(), goal.getVersion());
    }
}

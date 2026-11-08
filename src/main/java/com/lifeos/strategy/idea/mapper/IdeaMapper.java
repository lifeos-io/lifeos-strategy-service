package com.lifeos.strategy.idea.mapper;

import com.lifeos.strategy.idea.dto.IdeaResponse;
import com.lifeos.strategy.idea.dto.IdeaResponse.EvaluationResponse;
import com.lifeos.strategy.idea.entity.Idea;
import com.lifeos.strategy.idea.event.IdeaChangedV1;
import com.lifeos.strategy.idea.event.IdeaChangedV1.EvaluationV1;
import org.springframework.stereotype.Component;

@Component
public class IdeaMapper {

    public IdeaResponse toResponse(Idea idea) {
        return new IdeaResponse(idea.getId(), idea.getGoalId(), idea.getTitle(), idea.getNote(), idea.getStatus(),
                idea.getEvaluations().stream()
                        .map(e -> new EvaluationResponse(e.getGoalFit(), e.getEvidencePotential(), e.getEnergy(),
                                e.getWeeklyMinutes(), e.getVerdict(), e.getNote(), e.getEvaluatedAt()))
                        .toList(),
                idea.getActivatedAt(), idea.getClosedAt(), idea.getPromotedGoalId(), idea.getVersion(),
                idea.getCreatedAt(), idea.getUpdatedAt());
    }

    public IdeaChangedV1 toChangedEvent(Idea idea) {
        EvaluationV1 latest = idea.latestEvaluation()
                .map(e -> new EvaluationV1(e.getGoalFit(), e.getEvidencePotential(), e.getEnergy(),
                        e.getWeeklyMinutes(), e.getVerdict(), e.getNote(), e.getEvaluatedAt()))
                .orElse(null);
        return new IdeaChangedV1(idea.getId(), idea.getGoalId(), idea.getTitle(), idea.getNote(), idea.getStatus(),
                idea.getActivatedAt(), idea.getClosedAt(), idea.getPromotedGoalId(), latest,
                idea.getEvaluations().size(), idea.getVersion());
    }
}

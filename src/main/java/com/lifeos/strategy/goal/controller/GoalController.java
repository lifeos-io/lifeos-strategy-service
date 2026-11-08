package com.lifeos.strategy.goal.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.strategy.goal.dto.CreateGoalRequest;
import com.lifeos.strategy.goal.dto.GoalResponse;
import com.lifeos.strategy.goal.dto.ReorderGoalsRequest;
import com.lifeos.strategy.goal.dto.UpdateGoalRequest;
import com.lifeos.strategy.goal.entity.GoalStatus;
import com.lifeos.strategy.goal.service.GoalService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/strategy/goals}: the signed-in user's goals. */
@RestController
@RequestMapping("/api/strategy/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(@CurrentUser UUID userId,
            @Valid @RequestBody CreateGoalRequest request) {
        GoalResponse goal = goalService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/strategy/goals/" + goal.id())).body(goal);
    }

    /** By priority; {@code ?status=ACTIVE&status=PAUSED} filters, no parameter returns all goals. */
    @GetMapping
    public List<GoalResponse> list(@CurrentUser UUID userId,
            @RequestParam(name = "status", required = false) Set<GoalStatus> statuses) {
        return goalService.list(userId, statuses == null ? Set.of() : statuses);
    }

    @GetMapping("/{goalId}")
    public GoalResponse get(@CurrentUser UUID userId, @PathVariable UUID goalId) {
        return goalService.get(userId, goalId);
    }

    @PatchMapping("/{goalId}")
    public GoalResponse update(@CurrentUser UUID userId, @PathVariable UUID goalId,
            @Valid @RequestBody UpdateGoalRequest request) {
        return goalService.update(userId, goalId, request);
    }

    @PutMapping("/priority-order")
    public List<GoalResponse> reorder(@CurrentUser UUID userId, @Valid @RequestBody ReorderGoalsRequest request) {
        return goalService.reorder(userId, request);
    }
}

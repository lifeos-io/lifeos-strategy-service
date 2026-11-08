package com.lifeos.strategy.weeklyplan.controller;

import com.lifeos.base.web.security.CurrentUser;
import com.lifeos.strategy.weeklyplan.dto.CreateWeeklyPlanRequest;
import com.lifeos.strategy.weeklyplan.dto.UpdateWeeklyPlanRequest;
import com.lifeos.strategy.weeklyplan.dto.WeeklyPlanResponse;
import com.lifeos.strategy.weeklyplan.entity.WeeklyPlanStatus;
import com.lifeos.strategy.weeklyplan.service.WeeklyPlanService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/strategy/weekly-plans}: the user's weekly plans, addressed by ISO week (e.g. 2026-W46). */
@RestController
@RequestMapping("/api/strategy/weekly-plans")
public class WeeklyPlanController {

    private final WeeklyPlanService weeklyPlanService;

    public WeeklyPlanController(WeeklyPlanService weeklyPlanService) {
        this.weeklyPlanService = weeklyPlanService;
    }

    /** A draft for one week. */
    @PostMapping
    public ResponseEntity<WeeklyPlanResponse> create(@CurrentUser UUID userId,
            @Valid @RequestBody CreateWeeklyPlanRequest request) {
        WeeklyPlanResponse plan = weeklyPlanService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/strategy/weekly-plans/" + plan.isoWeek())).body(plan);
    }

    /** Newest week first; {@code ?status=ACTIVE} narrows the list. */
    @GetMapping
    public List<WeeklyPlanResponse> list(@CurrentUser UUID userId,
            @RequestParam(required = false) WeeklyPlanStatus status) {
        return weeklyPlanService.list(userId, status);
    }

    @GetMapping("/{isoWeek}")
    public WeeklyPlanResponse get(@CurrentUser UUID userId, @PathVariable String isoWeek) {
        return weeklyPlanService.get(userId, isoWeek);
    }

    @PatchMapping("/{isoWeek}")
    public WeeklyPlanResponse update(@CurrentUser UUID userId, @PathVariable String isoWeek,
            @Valid @RequestBody UpdateWeeklyPlanRequest request) {
        return weeklyPlanService.update(userId, isoWeek, request);
    }

    /** Commits the user to the week (DRAFT → ACTIVE). */
    @PostMapping("/{isoWeek}/activate")
    public WeeklyPlanResponse activate(@CurrentUser UUID userId, @PathVariable String isoWeek) {
        return weeklyPlanService.activate(userId, isoWeek);
    }

    /** Ends the week (ACTIVE → CLOSED); later done by the weekly review. */
    @PostMapping("/{isoWeek}/close")
    public WeeklyPlanResponse close(@CurrentUser UUID userId, @PathVariable String isoWeek) {
        return weeklyPlanService.close(userId, isoWeek);
    }
}

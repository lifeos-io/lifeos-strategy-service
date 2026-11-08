package com.lifeos.strategy.weeklyplan.service;

import com.lifeos.strategy.weeklyplan.dto.CreateWeeklyPlanRequest;
import com.lifeos.strategy.weeklyplan.dto.UpdateWeeklyPlanRequest;
import com.lifeos.strategy.weeklyplan.dto.WeeklyPlanResponse;
import com.lifeos.strategy.weeklyplan.entity.WeeklyPlanStatus;
import java.util.List;
import java.util.UUID;

/**
 * Weekly plans, addressed by ISO week ({@code 2026-W46}). Rules: one plan per week, the goals' minutes never
 * exceed the capacity, only active goals get minutes, one active week per user. Activating publishes
 * {@code WeeklyPlanActivated}; changing an active week's capacity publishes {@code CapacityChanged}.
 */
public interface WeeklyPlanService {

    WeeklyPlanResponse create(UUID userId, CreateWeeklyPlanRequest request);

    /** Newest week first; {@code status == null} returns all. */
    List<WeeklyPlanResponse> list(UUID userId, WeeklyPlanStatus status);

    WeeklyPlanResponse get(UUID userId, String isoWeek);

    WeeklyPlanResponse update(UUID userId, String isoWeek, UpdateWeeklyPlanRequest request);

    WeeklyPlanResponse activate(UUID userId, String isoWeek);

    WeeklyPlanResponse close(UUID userId, String isoWeek);
}

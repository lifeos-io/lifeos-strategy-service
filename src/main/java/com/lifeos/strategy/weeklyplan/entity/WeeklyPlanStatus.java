package com.lifeos.strategy.weeklyplan.entity;

/** DRAFT → ACTIVE (committed; one per user at a time) → CLOSED (read-only). */
public enum WeeklyPlanStatus {
    DRAFT,
    ACTIVE,
    CLOSED
}

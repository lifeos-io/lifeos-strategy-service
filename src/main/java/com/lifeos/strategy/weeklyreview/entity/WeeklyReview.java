package com.lifeos.strategy.weeklyreview.entity;

import com.lifeos.base.id.UuidV7;
import com.lifeos.strategy.common.persistence.AssignedIdEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The look-back that closes the user's active week: the numbers, counted when it is written (actions by status,
 * each goal's minutes against its share, evidence of the week), and the user's reflection. One per week; written
 * once and never changed, like evidence.
 */
@Entity
@Table(name = "weekly_review")
public class WeeklyReview extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID userId;
    private UUID weeklyPlanId;
    private String isoWeek;
    private int capacityMinutes;

    @Embedded
    private ActionTally actions;

    private int evidenceCount;
    private int energy;
    private String wentWell;
    private String gotStuck;
    private String lesson;
    private String nextWeekFocus;
    private Instant completedAt;
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "weekly_review_goal", joinColumns = @JoinColumn(name = "weekly_review_id"))
    private List<GoalOutcome> goals = new ArrayList<>();

    protected WeeklyReview() {
    }

    public static WeeklyReview complete(UUID userId, UUID weeklyPlanId, String isoWeek, int capacityMinutes,
            ActionTally actions, List<GoalOutcome> goals, int evidenceCount, int energy, String wentWell,
            String gotStuck, String lesson, String nextWeekFocus, Instant now) {
        WeeklyReview review = new WeeklyReview();
        review.id = UuidV7.next();
        review.userId = Objects.requireNonNull(userId);
        review.weeklyPlanId = Objects.requireNonNull(weeklyPlanId);
        review.isoWeek = Objects.requireNonNull(isoWeek);
        review.capacityMinutes = capacityMinutes;
        review.actions = Objects.requireNonNull(actions);
        review.goals.addAll(goals);
        review.evidenceCount = evidenceCount;
        review.energy = energy;
        review.wentWell = wentWell;
        review.gotStuck = gotStuck;
        review.lesson = lesson;
        review.nextWeekFocus = nextWeekFocus;
        review.completedAt = now;
        review.createdAt = now;
        return review;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getWeeklyPlanId() {
        return weeklyPlanId;
    }

    public String getIsoWeek() {
        return isoWeek;
    }

    public int getCapacityMinutes() {
        return capacityMinutes;
    }

    public ActionTally getActions() {
        return actions;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public int getEnergy() {
        return energy;
    }

    public String getWentWell() {
        return wentWell;
    }

    public String getGotStuck() {
        return gotStuck;
    }

    public String getLesson() {
        return lesson;
    }

    public String getNextWeekFocus() {
        return nextWeekFocus;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<GoalOutcome> getGoals() {
        return Collections.unmodifiableList(goals);
    }
}

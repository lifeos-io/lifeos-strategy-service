package com.lifeos.strategy.action.entity;

import com.lifeos.base.id.UuidV7;
import com.lifeos.strategy.common.persistence.AssignedIdEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One piece of work in a week's plan: what, why, which evidence it should produce, how long it takes, which
 * goals it serves and (optionally) which phase it moves forward. Its status follows the week.
 */
@Entity
@Table(name = "action")
public class Action extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID weeklyPlanId;
    private UUID userId;
    private UUID phaseKey;
    private String title;
    private String why;
    private String expectedEvidence;

    @Enumerated(EnumType.STRING)
    private ActionKind kind;

    private int estimateMinutes;

    @Enumerated(EnumType.STRING)
    private ActionStatus status;

    private int deferredCount;
    private int sortOrder;
    private Instant completedAt;

    @Version
    private int version;

    private Instant createdAt;
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "action_goal", joinColumns = @JoinColumn(name = "action_id"))
    @Column(name = "goal_id")
    private Set<UUID> goalIds = new HashSet<>();

    protected Action() {
    }

    public static Action create(UUID weeklyPlanId, UUID userId, Set<UUID> goalIds, UUID phaseKey, String title,
            String why, String expectedEvidence, ActionKind kind, int estimateMinutes, int sortOrder, Instant now) {
        Action action = new Action();
        action.id = UuidV7.next();
        action.weeklyPlanId = Objects.requireNonNull(weeklyPlanId);
        action.userId = Objects.requireNonNull(userId);
        action.goalIds.addAll(goalIds);
        action.phaseKey = phaseKey;
        action.title = Objects.requireNonNull(title);
        action.why = why;
        action.expectedEvidence = expectedEvidence;
        action.kind = Objects.requireNonNull(kind);
        action.estimateMinutes = estimateMinutes;
        action.status = ActionStatus.PLANNED;
        action.sortOrder = sortOrder;
        action.createdAt = now;
        action.updatedAt = now;
        return action;
    }

    /**
     * Partial update of the content; {@code null} arguments leave the field as it is, a non-null
     * {@code goalIds} replaces them.
     *
     * @return whether anything changed
     */
    public boolean update(Set<UUID> goalIds, UUID phaseKey, String title, String why, String expectedEvidence,
            ActionKind kind, Integer estimateMinutes, Instant now) {
        boolean changed = false;
        if (goalIds != null && !goalIds.equals(this.goalIds)) {
            this.goalIds.clear();
            this.goalIds.addAll(goalIds);
            changed = true;
        }
        if (phaseKey != null && !phaseKey.equals(this.phaseKey)) {
            this.phaseKey = phaseKey;
            changed = true;
        }
        if (title != null && !title.equals(this.title)) {
            this.title = title;
            changed = true;
        }
        if (why != null && !why.equals(this.why)) {
            this.why = why;
            changed = true;
        }
        if (expectedEvidence != null && !expectedEvidence.equals(this.expectedEvidence)) {
            this.expectedEvidence = expectedEvidence;
            changed = true;
        }
        if (kind != null && kind != this.kind) {
            this.kind = kind;
            changed = true;
        }
        if (estimateMinutes != null && estimateMinutes != this.estimateMinutes) {
            this.estimateMinutes = estimateMinutes;
            changed = true;
        }
        if (changed) {
            this.updatedAt = now;
        }
        return changed;
    }

    /**
     * Moves the action to {@code target}: becoming DEFERRED counts one more deferral, becoming DONE stamps the
     * completion time and leaving DONE clears it.
     *
     * @return whether the status changed
     */
    public boolean changeStatus(ActionStatus target, Instant now) {
        Objects.requireNonNull(target);
        if (target == status) {
            return false;
        }
        if (target == ActionStatus.DEFERRED) {
            deferredCount++;
        }
        completedAt = target == ActionStatus.DONE ? now : null;
        status = target;
        updatedAt = now;
        return true;
    }

    /** Whether the estimate counts against the week's capacity. */
    public boolean countsAgainstCapacity() {
        return status != ActionStatus.DROPPED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWeeklyPlanId() {
        return weeklyPlanId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPhaseKey() {
        return phaseKey;
    }

    public String getTitle() {
        return title;
    }

    public String getWhy() {
        return why;
    }

    public String getExpectedEvidence() {
        return expectedEvidence;
    }

    public ActionKind getKind() {
        return kind;
    }

    public int getEstimateMinutes() {
        return estimateMinutes;
    }

    public ActionStatus getStatus() {
        return status;
    }

    public int getDeferredCount() {
        return deferredCount;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<UUID> getGoalIds() {
        return Collections.unmodifiableSet(goalIds);
    }
}

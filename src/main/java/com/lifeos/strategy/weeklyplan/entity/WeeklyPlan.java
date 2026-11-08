package com.lifeos.strategy.weeklyplan.entity;

import com.lifeos.strategy.common.persistence.AssignedIdEntity;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.id.UuidV7;
import com.lifeos.strategy.common.error.StrategyErrorCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The user's plan for one ISO week: the capacity in minutes and its split across goals. A draft can be edited
 * freely; once activated the user is committed to it (one active week at a time); a closed week is read-only.
 * The capacity rule (allocations never exceed the capacity) is checked by the service before changes reach here.
 */
@Entity
@Table(name = "weekly_plan")
public class WeeklyPlan extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID userId;
    private String isoWeek;
    private LocalDate weekStart;
    private int capacityMinutes;
    private String headline;

    @Enumerated(EnumType.STRING)
    private WeeklyPlanStatus status;

    private Instant activatedAt;
    private Instant closedAt;

    @Version
    private int version;

    private Instant createdAt;
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "weekly_plan_goal", joinColumns = @JoinColumn(name = "weekly_plan_id"))
    private List<GoalAllocation> allocations = new ArrayList<>();

    protected WeeklyPlan() {
    }

    public static WeeklyPlan create(UUID userId, String isoWeek, LocalDate weekStart, int capacityMinutes,
            String headline, List<GoalAllocation> allocations, Instant now) {
        WeeklyPlan plan = new WeeklyPlan();
        plan.id = UuidV7.next();
        plan.userId = Objects.requireNonNull(userId);
        plan.isoWeek = Objects.requireNonNull(isoWeek);
        plan.weekStart = Objects.requireNonNull(weekStart);
        plan.capacityMinutes = capacityMinutes;
        plan.headline = headline;
        plan.status = WeeklyPlanStatus.DRAFT;
        plan.allocations.addAll(allocations);
        plan.createdAt = now;
        plan.updatedAt = now;
        return plan;
    }

    /**
     * Partial update; {@code null} arguments leave the field as it is, a non-null {@code allocations} replaces
     * them all. A closed week cannot change.
     *
     * @return whether anything changed
     */
    public boolean update(Integer capacityMinutes, String headline, List<GoalAllocation> allocations, Instant now) {
        requireEditable();
        boolean changed = false;
        if (capacityMinutes != null && capacityMinutes != this.capacityMinutes) {
            this.capacityMinutes = capacityMinutes;
            changed = true;
        }
        if (headline != null && !headline.equals(this.headline)) {
            this.headline = headline;
            changed = true;
        }
        if (allocations != null && !sameAllocations(allocations)) {
            this.allocations.clear();
            this.allocations.addAll(allocations);
            changed = true;
        }
        if (changed) {
            this.updatedAt = now;
        }
        return changed;
    }

    /** Commits the user to the week; {@code currentVersions} is each goal's current strategy version (if any). */
    public void activate(Map<UUID, UUID> currentVersions, Instant now) {
        requireStatus(WeeklyPlanStatus.DRAFT);
        List<GoalAllocation> stamped = allocations.stream()
                .map(a -> new GoalAllocation(a.getGoalId(), currentVersions.get(a.getGoalId()), a.getAllocatedMinutes()))
                .toList();
        allocations.clear();
        allocations.addAll(stamped);
        status = WeeklyPlanStatus.ACTIVE;
        activatedAt = now;
        updatedAt = now;
    }

    public void close(Instant now) {
        requireStatus(WeeklyPlanStatus.ACTIVE);
        status = WeeklyPlanStatus.CLOSED;
        closedAt = now;
        updatedAt = now;
    }

    /** Draft and active weeks can change; a closed week cannot. */
    public void requireEditable() {
        requireStatus(WeeklyPlanStatus.DRAFT, WeeklyPlanStatus.ACTIVE);
    }

    public int getAllocatedMinutes() {
        return allocations.stream().mapToInt(GoalAllocation::getAllocatedMinutes).sum();
    }

    private boolean sameAllocations(List<GoalAllocation> other) {
        if (other.size() != allocations.size()) {
            return false;
        }
        for (int i = 0; i < other.size(); i++) {
            GoalAllocation a = allocations.get(i);
            GoalAllocation b = other.get(i);
            if (!a.getGoalId().equals(b.getGoalId()) || a.getAllocatedMinutes() != b.getAllocatedMinutes()) {
                return false;
            }
        }
        return true;
    }

    private void requireStatus(WeeklyPlanStatus... allowed) {
        for (WeeklyPlanStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new LifeOsException(StrategyErrorCode.STRATEGY_PLAN_STATUS,
                "The plan for " + isoWeek + " is " + status.name().toLowerCase() + "; this is not possible now.",
                Map.of("currentStatus", status.name()));
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getIsoWeek() {
        return isoWeek;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public int getCapacityMinutes() {
        return capacityMinutes;
    }

    public String getHeadline() {
        return headline;
    }

    public WeeklyPlanStatus getStatus() {
        return status;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
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

    public List<GoalAllocation> getAllocations() {
        return Collections.unmodifiableList(allocations);
    }
}

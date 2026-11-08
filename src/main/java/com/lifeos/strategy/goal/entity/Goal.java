package com.lifeos.strategy.goal.entity;

import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.id.UuidV7;
import com.lifeos.strategy.common.error.FieldErrors;
import com.lifeos.strategy.common.error.StrategyErrorCode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Something the user wants to reach within a time window, e.g. "move to an in-house backend role by June".
 * A user can have several open goals; {@code priority} orders them (1 = most important) and decides which goal
 * wins when they compete for the week's capacity. Achieved and abandoned goals are read-only.
 */
@Entity
@Table(name = "goal")
public class Goal {

    @Id
    private UUID id;

    private UUID userId;
    private String title;

    @Enumerated(EnumType.STRING)
    private GoalDomain domain;

    private String whyItMatters;
    private String successDefinition;
    private LocalDate windowStart;
    private LocalDate windowEnd;

    @Enumerated(EnumType.STRING)
    private GoalStatus status;

    private int priority;

    @Version
    private int version;

    private Instant createdAt;
    private Instant updatedAt;

    protected Goal() {
    }

    public static Goal create(UUID userId, String title, GoalDomain domain, String whyItMatters,
            String successDefinition, LocalDate windowStart, LocalDate windowEnd, int priority, Instant now) {
        requireValidWindow(windowStart, windowEnd);
        Goal goal = new Goal();
        goal.id = UuidV7.next();
        goal.userId = Objects.requireNonNull(userId);
        goal.title = Objects.requireNonNull(title);
        goal.domain = Objects.requireNonNull(domain);
        goal.whyItMatters = whyItMatters;
        goal.successDefinition = successDefinition;
        goal.windowStart = windowStart;
        goal.windowEnd = windowEnd;
        goal.status = GoalStatus.ACTIVE;
        goal.priority = priority;
        goal.createdAt = now;
        goal.updatedAt = now;
        return goal;
    }

    /**
     * Partial update of the descriptive fields; {@code null} arguments leave the field as it is.
     *
     * @return whether anything changed
     */
    public boolean update(String title, GoalDomain domain, String whyItMatters, String successDefinition,
            LocalDate windowStart, LocalDate windowEnd, Instant now) {
        LocalDate start = windowStart != null ? windowStart : this.windowStart;
        LocalDate end = windowEnd != null ? windowEnd : this.windowEnd;
        boolean changed = !Objects.equals(start, this.windowStart) || !Objects.equals(end, this.windowEnd)
                || (title != null && !title.equals(this.title))
                || (domain != null && domain != this.domain)
                || (whyItMatters != null && !whyItMatters.equals(this.whyItMatters))
                || (successDefinition != null && !successDefinition.equals(this.successDefinition));
        if (!changed) {
            return false;
        }
        requireOpen();
        requireValidWindow(start, end);
        this.title = title != null ? title : this.title;
        this.domain = domain != null ? domain : this.domain;
        this.whyItMatters = whyItMatters != null ? whyItMatters : this.whyItMatters;
        this.successDefinition = successDefinition != null ? successDefinition : this.successDefinition;
        this.windowStart = start;
        this.windowEnd = end;
        this.updatedAt = now;
        return true;
    }

    /** @return whether the status changed ({@code false} when it already had that status) */
    public boolean changeStatus(GoalStatus target, Instant now) {
        Objects.requireNonNull(target);
        if (target == status) {
            return false;
        }
        if (!status.canChangeTo(target)) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_GOAL_STATUS_TRANSITION,
                    "A " + status.name().toLowerCase() + " goal cannot become " + target.name().toLowerCase() + ".",
                    Map.of("from", status.name(), "to", target.name()));
        }
        status = target;
        updatedAt = now;
        return true;
    }

    /** @return whether the priority changed */
    public boolean reprioritize(int priority, Instant now) {
        requireOpen();
        if (priority == this.priority) {
            return false;
        }
        this.priority = priority;
        this.updatedAt = now;
        return true;
    }

    private void requireOpen() {
        if (!status.isOpen()) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_GOAL_CLOSED,
                    "This goal is " + status.name().toLowerCase() + " and can no longer be changed.");
        }
    }

    private static void requireValidWindow(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw FieldErrors.invalid("windowEnd", "must not be before windowStart");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public GoalDomain getDomain() {
        return domain;
    }

    public String getWhyItMatters() {
        return whyItMatters;
    }

    public String getSuccessDefinition() {
        return successDefinition;
    }

    public LocalDate getWindowStart() {
        return windowStart;
    }

    public LocalDate getWindowEnd() {
        return windowEnd;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public int getPriority() {
        return priority;
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
}

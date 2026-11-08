package com.lifeos.strategy.idea.entity;

import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.id.UuidV7;
import com.lifeos.strategy.common.error.StrategyErrorCode;
import com.lifeos.strategy.common.persistence.AssignedIdEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An idea the user captured so it is neither lost nor allowed to steal focus. It waits in the INBOX, is evaluated
 * (possibly several times), and only an idea whose latest verdict is PURSUE can take one of the user's two ACTIVE
 * slots (the limit is checked by the service, which can see the user's other ideas). An active idea is shelved,
 * dropped, or promoted into a goal. Dropped and promoted ideas are read-only.
 */
@Entity
@Table(name = "idea")
public class Idea extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID userId;
    private UUID goalId;
    private String title;
    private String note;

    @Enumerated(EnumType.STRING)
    private IdeaStatus status;

    private Instant activatedAt;
    private Instant closedAt;
    private UUID promotedGoalId;

    @Version
    private int version;

    private Instant createdAt;
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "idea_evaluation", joinColumns = @JoinColumn(name = "idea_id"))
    @OrderColumn(name = "position")
    private List<IdeaEvaluation> evaluations = new ArrayList<>();

    protected Idea() {
    }

    /** A new idea in the INBOX. */
    public static Idea capture(UUID userId, UUID goalId, String title, String note, Instant now) {
        Idea idea = new Idea();
        idea.id = UuidV7.next();
        idea.userId = Objects.requireNonNull(userId);
        idea.goalId = goalId;
        idea.title = Objects.requireNonNull(title);
        idea.note = note;
        idea.status = IdeaStatus.INBOX;
        idea.createdAt = now;
        idea.updatedAt = now;
        return idea;
    }

    /**
     * Changes the given fields; {@code null} leaves a field as it is.
     *
     * @return whether anything changed
     */
    public boolean edit(UUID goalId, String title, String note, Instant now) {
        requireOpen();
        boolean changed = false;
        if (goalId != null && !goalId.equals(this.goalId)) {
            this.goalId = goalId;
            changed = true;
        }
        if (title != null && !title.equals(this.title)) {
            this.title = title;
            changed = true;
        }
        if (note != null && !note.equals(this.note)) {
            this.note = note;
            changed = true;
        }
        if (changed) {
            this.updatedAt = now;
        }
        return changed;
    }

    public void evaluate(IdeaEvaluation evaluation, Instant now) {
        requireOpen();
        evaluations.add(Objects.requireNonNull(evaluation));
        this.updatedAt = now;
    }

    /** INBOX → ACTIVE. Needs a latest evaluation with verdict PURSUE; the caller has checked the active limit. */
    public void activate(Instant now) {
        requireTransition(IdeaStatus.ACTIVE);
        IdeaVerdict verdict = latestEvaluation().map(IdeaEvaluation::getVerdict).orElse(null);
        if (verdict != IdeaVerdict.PURSUE) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_IDEA_NOT_PURSUED,
                    verdict == null
                            ? "Evaluate the idea before making it active."
                            : "The idea's latest evaluation says " + verdict.name().toLowerCase()
                                    + "; only an idea worth pursuing can become active.",
                    verdict == null ? Map.of() : Map.of("verdict", verdict.name()));
        }
        this.activatedAt = now;
        moveTo(IdeaStatus.ACTIVE, now);
    }

    /** INBOX or ACTIVE → SHELVED: not now, maybe later. */
    public void shelve(Instant now) {
        requireTransition(IdeaStatus.SHELVED);
        moveTo(IdeaStatus.SHELVED, now);
    }

    /** SHELVED → INBOX. */
    public void reopen(Instant now) {
        requireTransition(IdeaStatus.INBOX);
        moveTo(IdeaStatus.INBOX, now);
    }

    /** INBOX, ACTIVE or SHELVED → DROPPED (final). */
    public void drop(Instant now) {
        requireTransition(IdeaStatus.DROPPED);
        this.closedAt = now;
        moveTo(IdeaStatus.DROPPED, now);
    }

    /** ACTIVE → PROMOTED (final): the idea grew into {@code goalId}. */
    public void promote(UUID goalId, Instant now) {
        requireTransition(IdeaStatus.PROMOTED);
        this.promotedGoalId = Objects.requireNonNull(goalId);
        this.closedAt = now;
        moveTo(IdeaStatus.PROMOTED, now);
    }

    public Optional<IdeaEvaluation> latestEvaluation() {
        return evaluations.isEmpty() ? Optional.empty() : Optional.of(evaluations.getLast());
    }

    private void requireOpen() {
        if (status.isClosed()) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_IDEA_STATUS,
                    "This idea is " + status.name().toLowerCase() + " and can no longer change.",
                    Map.of("currentStatus", status.name()));
        }
    }

    private void requireTransition(IdeaStatus target) {
        if (!status.canChangeTo(target)) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_IDEA_STATUS,
                    "An idea that is " + status.name().toLowerCase() + " cannot become "
                            + target.name().toLowerCase() + ".",
                    Map.of("currentStatus", status.name(), "target", target.name()));
        }
    }

    private void moveTo(IdeaStatus target, Instant now) {
        this.status = target;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public String getTitle() {
        return title;
    }

    public String getNote() {
        return note;
    }

    public IdeaStatus getStatus() {
        return status;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public UUID getPromotedGoalId() {
        return promotedGoalId;
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

    public List<IdeaEvaluation> getEvaluations() {
        return Collections.unmodifiableList(evaluations);
    }
}

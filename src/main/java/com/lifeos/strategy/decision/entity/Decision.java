package com.lifeos.strategy.decision.entity;

import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.id.UuidV7;
import com.lifeos.strategy.common.error.StrategyErrorCode;
import com.lifeos.strategy.common.persistence.AssignedIdEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A long-term decision. While DRAFT everything can change; {@link #decide} commits to one alternative with an
 * expected outcome and a review date (OPEN); {@link #close} records the look-back (CLOSED). Decided decisions
 * are never edited: their history is the point.
 */
@Entity
@Table(name = "decision")
public class Decision extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID userId;
    private UUID goalId;
    private String title;
    private String context;

    @Enumerated(EnumType.STRING)
    private DecisionStatus status;

    private String expectedOutcome;
    private Instant decidedAt;
    private LocalDate reviewOn;
    private String outcomeNote;

    @Enumerated(EnumType.STRING)
    private WouldDecideAgain wouldDecideAgain;

    private Instant closedAt;

    @Version
    private int version;

    private Instant createdAt;
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "decision_alternative", joinColumns = @JoinColumn(name = "decision_id"))
    @OrderColumn(name = "position")
    private List<Alternative> alternatives = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "decision_assumption", joinColumns = @JoinColumn(name = "decision_id"))
    @Column(name = "knowledge_item_id")
    private Set<UUID> assumptionIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "decision_reopen_trigger", joinColumns = @JoinColumn(name = "decision_id"))
    @OrderColumn(name = "position")
    private List<ReopenTrigger> reopenTriggers = new ArrayList<>();

    protected Decision() {
    }

    /** @param alternativeDescriptions labelled A, B, C... in this order */
    public static Decision draft(UUID userId, UUID goalId, String title, String context,
            List<String> alternativeDescriptions, Map<String, String> rejectionReasons, Set<UUID> assumptionIds,
            List<ReopenTrigger> reopenTriggers, Instant now) {
        Decision decision = new Decision();
        decision.id = UuidV7.next();
        decision.userId = Objects.requireNonNull(userId);
        decision.status = DecisionStatus.DRAFT;
        decision.createdAt = now;
        decision.revise(goalId, title, context, alternativeDescriptions, rejectionReasons, assumptionIds,
                reopenTriggers, now);
        return decision;
    }

    /** Replaces the draft's content (a draft is edited as a whole). */
    public void revise(UUID goalId, String title, String context, List<String> alternativeDescriptions,
            Map<String, String> rejectionReasons, Set<UUID> assumptionIds, List<ReopenTrigger> reopenTriggers,
            Instant now) {
        requireStatus(DecisionStatus.DRAFT);
        this.goalId = goalId;
        this.title = Objects.requireNonNull(title);
        this.context = context;
        List<Alternative> labelled = new ArrayList<>();
        for (int i = 0; i < alternativeDescriptions.size(); i++) {
            String label = label(i);
            labelled.add(new Alternative(label, alternativeDescriptions.get(i), false, rejectionReasons.get(label)));
        }
        replace(this.alternatives, labelled);
        this.assumptionIds.retainAll(assumptionIds);
        this.assumptionIds.addAll(assumptionIds);
        replace(this.reopenTriggers, reopenTriggers);
        this.updatedAt = now;
    }

    /**
     * Commits to the alternative with {@code chosenLabel}. The caller has checked the rules (two or more
     * alternatives, a rejection reason for each one not chosen, a review date in the future).
     */
    public void decide(String chosenLabel, String expectedOutcome, LocalDate reviewOn, Instant now) {
        requireStatus(DecisionStatus.DRAFT);
        List<Alternative> decided = alternatives.stream()
                .map(a -> a.getLabel().equals(chosenLabel)
                        ? new Alternative(a.getLabel(), a.getDescription(), true, null)
                        : a)
                .toList();
        replace(alternatives, decided);
        this.expectedOutcome = Objects.requireNonNull(expectedOutcome);
        this.reviewOn = Objects.requireNonNull(reviewOn);
        this.decidedAt = now;
        this.status = DecisionStatus.OPEN;
        this.updatedAt = now;
    }

    public void close(String outcomeNote, WouldDecideAgain wouldDecideAgain, Instant now) {
        requireStatus(DecisionStatus.OPEN);
        this.outcomeNote = Objects.requireNonNull(outcomeNote);
        this.wouldDecideAgain = Objects.requireNonNull(wouldDecideAgain);
        this.closedAt = now;
        this.status = DecisionStatus.CLOSED;
        this.updatedAt = now;
    }

    public void requireStatus(DecisionStatus expected) {
        if (status != expected) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_DECISION_STATUS,
                    "This decision is " + status.name().toLowerCase() + "; this needs it to be "
                            + expected.name().toLowerCase() + ".",
                    Map.of("status", status.name()));
        }
    }

    /** A, B, C... for positions 0, 1, 2... */
    public static String label(int position) {
        return String.valueOf((char) ('A' + position));
    }

    private static <T> void replace(List<T> target, List<T> values) {
        if (!target.equals(values)) {
            target.clear();
            target.addAll(values);
        }
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

    public String getContext() {
        return context;
    }

    public DecisionStatus getStatus() {
        return status;
    }

    public String getExpectedOutcome() {
        return expectedOutcome;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public LocalDate getReviewOn() {
        return reviewOn;
    }

    public String getOutcomeNote() {
        return outcomeNote;
    }

    public WouldDecideAgain getWouldDecideAgain() {
        return wouldDecideAgain;
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

    public List<Alternative> getAlternatives() {
        return Collections.unmodifiableList(alternatives);
    }

    public Set<UUID> getAssumptionIds() {
        return Collections.unmodifiableSet(assumptionIds);
    }

    public List<ReopenTrigger> getReopenTriggers() {
        return Collections.unmodifiableList(reopenTriggers);
    }
}

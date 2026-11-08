package com.lifeos.strategy.evidence.entity;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A piece of evidence of real-life progress, the core of "measurable outcomes". It can build on a lower step of
 * the chain (activity → output → outcome → impact), come from an action or a feedback, serve goals and count
 * towards phases' exit criteria. Immutable once recorded.
 */
@Entity
@Table(name = "evidence")
public class Evidence extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID userId;
    private String title;

    @Enumerated(EnumType.STRING)
    private EvidenceLevel level;

    @Enumerated(EnumType.STRING)
    private EvidenceStrength strength;

    private LocalDate occurredOn;
    private UUID parentEvidenceId;
    private UUID actionId;
    private UUID feedbackId;
    private String note;
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "evidence_goal", joinColumns = @JoinColumn(name = "evidence_id"))
    @Column(name = "goal_id")
    private Set<UUID> goalIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "evidence_phase", joinColumns = @JoinColumn(name = "evidence_id"))
    @Column(name = "phase_key")
    private Set<UUID> phaseKeys = new HashSet<>();

    protected Evidence() {
    }

    public static Evidence record(UUID userId, String title, EvidenceLevel level, EvidenceStrength strength,
            LocalDate occurredOn, UUID parentEvidenceId, UUID actionId, UUID feedbackId, String note,
            Set<UUID> goalIds, Set<UUID> phaseKeys, Instant now) {
        Evidence evidence = new Evidence();
        evidence.id = UuidV7.next();
        evidence.userId = Objects.requireNonNull(userId);
        evidence.title = Objects.requireNonNull(title);
        evidence.level = Objects.requireNonNull(level);
        evidence.strength = Objects.requireNonNull(strength);
        evidence.occurredOn = Objects.requireNonNull(occurredOn);
        evidence.parentEvidenceId = parentEvidenceId;
        evidence.actionId = actionId;
        evidence.feedbackId = feedbackId;
        evidence.note = note;
        evidence.goalIds.addAll(goalIds);
        evidence.phaseKeys.addAll(phaseKeys);
        evidence.createdAt = now;
        return evidence;
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

    public EvidenceLevel getLevel() {
        return level;
    }

    public EvidenceStrength getStrength() {
        return strength;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public UUID getParentEvidenceId() {
        return parentEvidenceId;
    }

    public UUID getActionId() {
        return actionId;
    }

    public UUID getFeedbackId() {
        return feedbackId;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<UUID> getGoalIds() {
        return Collections.unmodifiableSet(goalIds);
    }

    public Set<UUID> getPhaseKeys() {
        return Collections.unmodifiableSet(phaseKeys);
    }
}

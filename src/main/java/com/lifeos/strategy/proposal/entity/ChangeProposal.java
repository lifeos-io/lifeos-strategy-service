package com.lifeos.strategy.proposal.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * A change Coach proposed for the user's plan. It waits (PENDING) until the user accepts it, which applies it
 * as a new strategy version, or rejects it; a newer proposal for the same goal supersedes it. Decided proposals
 * never change. {@code revision} is the proposed version as Coach sent it (JSON), applied through the same
 * validation as a revision the user writes.
 */
@Entity
@Table(name = "change_proposal")
public class ChangeProposal extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID userId;
    private UUID recommendationId;

    @Enumerated(EnumType.STRING)
    private ProposalKind kind;

    private UUID goalId;
    private Integer basedOnVersionNo;
    private String rationale;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String revision;

    @Enumerated(EnumType.STRING)
    private ProposalStatus status;

    private String decisionNote;
    private Instant decidedAt;
    private UUID appliedStrategyVersionId;

    @Version
    private int version;

    private Instant createdAt;
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "change_proposal_source", joinColumns = @JoinColumn(name = "change_proposal_id"))
    private Set<ProposalSource> sources = new HashSet<>();

    protected ChangeProposal() {
    }

    /** A new PENDING proposal; {@code revision} is the proposed version as JSON. */
    public static ChangeProposal receive(UUID userId, UUID recommendationId, ProposalKind kind, UUID goalId,
            Integer basedOnVersionNo, String rationale, String revision, Set<ProposalSource> sources, Instant now) {
        ChangeProposal proposal = new ChangeProposal();
        proposal.id = UuidV7.next();
        proposal.userId = Objects.requireNonNull(userId);
        proposal.recommendationId = Objects.requireNonNull(recommendationId);
        proposal.kind = Objects.requireNonNull(kind);
        proposal.goalId = Objects.requireNonNull(goalId);
        proposal.basedOnVersionNo = basedOnVersionNo;
        proposal.rationale = Objects.requireNonNull(rationale);
        proposal.revision = Objects.requireNonNull(revision);
        proposal.sources.addAll(sources);
        proposal.status = ProposalStatus.PENDING;
        proposal.createdAt = now;
        proposal.updatedAt = now;
        return proposal;
    }

    /** PENDING → ACCEPTED: the proposal became {@code strategyVersionId}. */
    public void accept(UUID strategyVersionId, Instant now) {
        decide(ProposalStatus.ACCEPTED, null, now);
        this.appliedStrategyVersionId = Objects.requireNonNull(strategyVersionId);
    }

    /** PENDING → REJECTED, with the user's reason if they gave one. */
    public void reject(String note, Instant now) {
        decide(ProposalStatus.REJECTED, note, now);
    }

    /** PENDING → SUPERSEDED: a newer proposal for the same goal replaces this one. */
    public void supersede(Instant now) {
        decide(ProposalStatus.SUPERSEDED, null, now);
    }

    /** Only a pending proposal can be decided. */
    public void requirePending() {
        if (status != ProposalStatus.PENDING) {
            throw new LifeOsException(StrategyErrorCode.STRATEGY_PROPOSAL_STATUS,
                    "This proposal was already " + status.name().toLowerCase() + ".",
                    Map.of("currentStatus", status.name()));
        }
    }

    private void decide(ProposalStatus target, String note, Instant now) {
        requirePending();
        this.status = target;
        this.decisionNote = note;
        this.decidedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRecommendationId() {
        return recommendationId;
    }

    public ProposalKind getKind() {
        return kind;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public Integer getBasedOnVersionNo() {
        return basedOnVersionNo;
    }

    public String getRationale() {
        return rationale;
    }

    public String getRevision() {
        return revision;
    }

    public ProposalStatus getStatus() {
        return status;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public UUID getAppliedStrategyVersionId() {
        return appliedStrategyVersionId;
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

    public Set<ProposalSource> getSources() {
        return Collections.unmodifiableSet(sources);
    }
}

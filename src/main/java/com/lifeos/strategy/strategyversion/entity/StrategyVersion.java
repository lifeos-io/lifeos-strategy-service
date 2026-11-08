package com.lifeos.strategy.strategyversion.entity;

import com.lifeos.strategy.common.persistence.AssignedIdEntity;
import com.lifeos.base.id.UuidV7;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a goal's strategy: a summary, its phases and the effort split. Versions never change once
 * written (only {@code current} moves to the newest one); a revision is a new version with
 * {@code versionNo + 1} and a reason. Old versions stay as the history of how the plan evolved.
 */
@Entity
@Table(name = "strategy_version")
public class StrategyVersion extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID goalId;
    private UUID userId;
    private int versionNo;
    private String summary;
    private String changeReason;
    private UUID sourceProposalId;

    @Column(name = "is_current")
    private boolean current;

    private Instant createdAt;

    @OneToMany(mappedBy = "strategyVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordinal")
    private List<Phase> phases = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "effort_allocation", joinColumns = @JoinColumn(name = "strategy_version_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "category")
    @Column(name = "percent")
    private Map<EffortCategory, Integer> effortAllocation = new EnumMap<>(EffortCategory.class);

    protected StrategyVersion() {
    }

    /** A new current version; the caller retires the previous one first. */
    public static StrategyVersion create(UUID goalId, UUID userId, int versionNo, String summary,
            String changeReason, UUID sourceProposalId, Map<EffortCategory, Integer> effortAllocation,
            Instant now) {
        StrategyVersion version = new StrategyVersion();
        version.id = UuidV7.next();
        version.goalId = Objects.requireNonNull(goalId);
        version.userId = Objects.requireNonNull(userId);
        version.versionNo = versionNo;
        version.summary = Objects.requireNonNull(summary);
        version.changeReason = Objects.requireNonNull(changeReason);
        version.sourceProposalId = sourceProposalId;
        version.current = true;
        version.createdAt = now;
        for (EffortCategory category : EffortCategory.values()) {
            version.effortAllocation.put(category, effortAllocation.getOrDefault(category, 0));
        }
        return version;
    }

    /** Appends the next phase (ordinals follow the order of the calls). */
    public void addPhase(UUID phaseKey, String title, LocalDate startsOn, LocalDate endsOn, String exitCriteria,
            PhaseStatus status) {
        phases.add(new Phase(this, phaseKey, phases.size() + 1, title, startsOn, endsOn, exitCriteria, status));
    }

    /** The newer version takes over as the goal's current strategy. */
    public void retire() {
        current = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getSummary() {
        return summary;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public UUID getSourceProposalId() {
        return sourceProposalId;
    }

    public boolean isCurrent() {
        return current;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Phase> getPhases() {
        return Collections.unmodifiableList(phases);
    }

    /** All categories, in enum order. */
    public Map<EffortCategory, Integer> getEffortAllocation() {
        Map<EffortCategory, Integer> copy = new EnumMap<>(EffortCategory.class);
        copy.putAll(effortAllocation);
        return copy;
    }
}

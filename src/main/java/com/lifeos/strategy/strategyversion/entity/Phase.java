package com.lifeos.strategy.strategyversion.entity;

import com.lifeos.base.id.UuidV7;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A stage of a strategy version, e.g. "Build the foundation" with its exit criteria. Part of the version and
 * immutable with it. {@code phaseKey} identifies the same phase across versions.
 */
@Entity
@Table(name = "phase")
public class Phase {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_version_id")
    private StrategyVersion strategyVersion;

    private UUID phaseKey;
    private int ordinal;
    private String title;
    private LocalDate startsOn;
    private LocalDate endsOn;
    private String exitCriteria;

    @Enumerated(EnumType.STRING)
    private PhaseStatus status;

    protected Phase() {
    }

    Phase(StrategyVersion strategyVersion, UUID phaseKey, int ordinal, String title, LocalDate startsOn,
            LocalDate endsOn, String exitCriteria, PhaseStatus status) {
        this.id = UuidV7.next();
        this.strategyVersion = strategyVersion;
        this.phaseKey = phaseKey;
        this.ordinal = ordinal;
        this.title = title;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.exitCriteria = exitCriteria;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPhaseKey() {
        return phaseKey;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public String getExitCriteria() {
        return exitCriteria;
    }

    public PhaseStatus getStatus() {
        return status;
    }
}

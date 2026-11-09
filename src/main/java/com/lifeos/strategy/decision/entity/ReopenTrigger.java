package com.lifeos.strategy.decision.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What should make the user reconsider a decision. {@code firedAt} is set once it happened (the trigger
 * service watches dates and events).
 */
@Embeddable
public class ReopenTrigger {

    @Enumerated(EnumType.STRING)
    private TriggerKind kind;

    private String description;
    private String eventType;
    private LocalDate firesOn;
    private Instant firedAt;

    protected ReopenTrigger() {
    }

    public ReopenTrigger(TriggerKind kind, String description, String eventType, LocalDate firesOn) {
        this.kind = Objects.requireNonNull(kind);
        this.description = Objects.requireNonNull(description);
        this.eventType = eventType;
        this.firesOn = firesOn;
    }

    /** The same trigger, marked as fired at {@code firedAt} (embeddables are values: a new one replaces it). */
    public ReopenTrigger fired(Instant firedAt) {
        ReopenTrigger fired = new ReopenTrigger(kind, description, eventType, firesOn);
        fired.firedAt = Objects.requireNonNull(firedAt);
        return fired;
    }

    public TriggerKind getKind() {
        return kind;
    }

    public String getDescription() {
        return description;
    }

    public String getEventType() {
        return eventType;
    }

    public LocalDate getFiresOn() {
        return firesOn;
    }

    public Instant getFiredAt() {
        return firedAt;
    }

    // Value equality, so Hibernate does not rewrite the collection on every flush.
    @Override
    public boolean equals(Object other) {
        return other instanceof ReopenTrigger that
                && kind == that.kind
                && description.equals(that.description)
                && Objects.equals(eventType, that.eventType)
                && Objects.equals(firesOn, that.firesOn)
                && Objects.equals(firedAt, that.firedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, description, eventType, firesOn, firedAt);
    }
}

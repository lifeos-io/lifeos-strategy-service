package com.lifeos.strategy.decision.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/** One option weighed in a decision, labelled A, B, C... by its position. */
@Embeddable
public class Alternative {

    private String label;
    private String description;

    @Column(name = "is_chosen")
    private boolean chosen;

    private String rejectionReason;

    protected Alternative() {
    }

    public Alternative(String label, String description, boolean chosen, String rejectionReason) {
        this.label = Objects.requireNonNull(label);
        this.description = Objects.requireNonNull(description);
        this.chosen = chosen;
        this.rejectionReason = rejectionReason;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public boolean isChosen() {
        return chosen;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    // Value equality, so Hibernate does not rewrite the collection on every flush.
    @Override
    public boolean equals(Object other) {
        return other instanceof Alternative that
                && chosen == that.chosen
                && label.equals(that.label)
                && description.equals(that.description)
                && Objects.equals(rejectionReason, that.rejectionReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, description, chosen, rejectionReason);
    }
}

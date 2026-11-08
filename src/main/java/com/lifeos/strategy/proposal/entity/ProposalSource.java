package com.lifeos.strategy.proposal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import java.util.UUID;

/** One record that prompted a proposal, e.g. the feedback behind it. */
@Embeddable
public class ProposalSource {

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private SourceType type;

    @Column(name = "source_id")
    private UUID id;

    protected ProposalSource() {
    }

    public ProposalSource(SourceType type, UUID id) {
        this.type = Objects.requireNonNull(type);
        this.id = Objects.requireNonNull(id);
    }

    public SourceType getType() {
        return type;
    }

    public UUID getId() {
        return id;
    }

    // Value equality, so Hibernate does not rewrite the collection on every flush.
    @Override
    public boolean equals(Object other) {
        return other instanceof ProposalSource that && type == that.type && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }
}

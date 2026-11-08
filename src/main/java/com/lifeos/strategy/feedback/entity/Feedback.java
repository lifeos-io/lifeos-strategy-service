package com.lifeos.strategy.feedback.entity;

import com.lifeos.base.id.UuidV7;
import com.lifeos.strategy.common.persistence.AssignedIdEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Feedback the user received, recorded as said. Immutable once recorded. */
@Entity
@Table(name = "feedback")
public class Feedback extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private FeedbackSourceKind sourceKind;

    private String sourceLabel;
    private String content;

    @Enumerated(EnumType.STRING)
    private Reliability reliability;

    private LocalDate receivedOn;
    private Instant createdAt;

    protected Feedback() {
    }

    public static Feedback record(UUID userId, FeedbackSourceKind sourceKind, String sourceLabel, String content,
            Reliability reliability, LocalDate receivedOn, Instant now) {
        Feedback feedback = new Feedback();
        feedback.id = UuidV7.next();
        feedback.userId = Objects.requireNonNull(userId);
        feedback.sourceKind = Objects.requireNonNull(sourceKind);
        feedback.sourceLabel = sourceLabel;
        feedback.content = Objects.requireNonNull(content);
        feedback.reliability = Objects.requireNonNull(reliability);
        feedback.receivedOn = Objects.requireNonNull(receivedOn);
        feedback.createdAt = now;
        return feedback;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public FeedbackSourceKind getSourceKind() {
        return sourceKind;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public String getContent() {
        return content;
    }

    public Reliability getReliability() {
        return reliability;
    }

    public LocalDate getReceivedOn() {
        return receivedOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

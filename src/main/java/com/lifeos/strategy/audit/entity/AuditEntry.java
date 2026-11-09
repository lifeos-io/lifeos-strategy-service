package com.lifeos.strategy.audit.entity;

import com.lifeos.base.id.UuidV7;
import com.lifeos.strategy.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * One audited operation. Append-only and kept apart from the user's data, so it outlives a deletion; it holds no
 * personal content, only the user's ID, what happened, who did it and technical details (JSON).
 */
@Entity
@Table(name = "audit_log")
public class AuditEntry extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    private AuditActor actor;

    private String correlationId;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String details;

    private Instant occurredAt;

    protected AuditEntry() {
    }

    /** {@code details} is a JSON object. */
    public static AuditEntry of(UUID userId, AuditAction action, AuditActor actor, String correlationId,
            String details, Instant now) {
        AuditEntry entry = new AuditEntry();
        entry.id = UuidV7.next();
        entry.userId = Objects.requireNonNull(userId);
        entry.action = Objects.requireNonNull(action);
        entry.actor = Objects.requireNonNull(actor);
        entry.correlationId = correlationId;
        entry.details = Objects.requireNonNull(details);
        entry.occurredAt = now;
        return entry;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditActor getActor() {
        return actor;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}

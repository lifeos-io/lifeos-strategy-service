package com.lifeos.strategy.knowledge.entity;

import com.lifeos.base.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One entry of a goal's knowledge map: a fact, an assumption or an unknown, how far it is verified, and how to
 * verify it. {@code verifiedAt} records when it was settled (verified or falsified) and is cleared when it is
 * reopened.
 */
@Entity
@Table(name = "knowledge_item")
public class KnowledgeItem {

    @Id
    private UUID id;

    private UUID goalId;
    private UUID userId;
    private String statement;

    @Enumerated(EnumType.STRING)
    private KnowledgeKind kind;

    @Enumerated(EnumType.STRING)
    private KnowledgeStatus status;

    @Column(name = "is_external")
    private boolean external;

    private String source;
    private String howToVerify;
    private Instant verifiedAt;

    @Version
    private int version;

    private Instant createdAt;
    private Instant updatedAt;

    protected KnowledgeItem() {
    }

    public static KnowledgeItem create(UUID goalId, UUID userId, String statement, KnowledgeKind kind,
            KnowledgeStatus status, boolean external, String source, String howToVerify, Instant now) {
        KnowledgeItem item = new KnowledgeItem();
        item.id = UuidV7.next();
        item.goalId = Objects.requireNonNull(goalId);
        item.userId = Objects.requireNonNull(userId);
        item.statement = Objects.requireNonNull(statement);
        item.kind = Objects.requireNonNull(kind);
        item.status = Objects.requireNonNull(status);
        item.external = external;
        item.source = source;
        item.howToVerify = howToVerify;
        item.verifiedAt = status.isSettled() ? now : null;
        item.createdAt = now;
        item.updatedAt = now;
        return item;
    }

    /**
     * Partial update; {@code null} arguments leave the field as it is.
     *
     * @return whether anything changed
     */
    public boolean update(String statement, KnowledgeKind kind, KnowledgeStatus status, Boolean external,
            String source, String howToVerify, Instant now) {
        boolean changed = false;
        if (statement != null && !statement.equals(this.statement)) {
            this.statement = statement;
            changed = true;
        }
        if (kind != null && kind != this.kind) {
            this.kind = kind;
            changed = true;
        }
        if (external != null && external != this.external) {
            this.external = external;
            changed = true;
        }
        if (source != null && !source.equals(this.source)) {
            this.source = source;
            changed = true;
        }
        if (howToVerify != null && !howToVerify.equals(this.howToVerify)) {
            this.howToVerify = howToVerify;
            changed = true;
        }
        if (status != null && status != this.status) {
            // Settling stamps the time; reopening clears it. Moving between VERIFIED and FALSIFIED re-stamps it.
            this.verifiedAt = status.isSettled() ? now : null;
            this.status = status;
            changed = true;
        }
        if (changed) {
            this.updatedAt = now;
        }
        return changed;
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

    public String getStatement() {
        return statement;
    }

    public KnowledgeKind getKind() {
        return kind;
    }

    public KnowledgeStatus getStatus() {
        return status;
    }

    public boolean isExternal() {
        return external;
    }

    public String getSource() {
        return source;
    }

    public String getHowToVerify() {
        return howToVerify;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
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
}

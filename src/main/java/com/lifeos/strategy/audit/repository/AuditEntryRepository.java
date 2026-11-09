package com.lifeos.strategy.audit.repository;

import com.lifeos.strategy.audit.entity.AuditAction;
import com.lifeos.strategy.audit.entity.AuditEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

    boolean existsByUserIdAndAction(UUID userId, AuditAction action);

    /** Oldest first. */
    List<AuditEntry> findByUserIdOrderByOccurredAtAsc(UUID userId);
}

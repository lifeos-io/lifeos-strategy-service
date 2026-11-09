package com.lifeos.strategy.audit.service;

import com.lifeos.strategy.audit.entity.AuditAction;
import com.lifeos.strategy.audit.entity.AuditActor;
import java.util.Map;
import java.util.UUID;

/**
 * The audit trail of sensitive operations. A record is written in the transaction of the operation it records,
 * so either both happen or neither; the request's or event's correlation ID is taken from the MDC.
 */
public interface AuditService {

    /** @param details technical facts only (counts, status codes), never personal content */
    void record(UUID userId, AuditAction action, AuditActor actor, Map<String, ?> details);

    boolean wasRecorded(UUID userId, AuditAction action);
}

package com.lifeos.strategy.audit.service.impl;

import com.lifeos.base.correlation.CorrelationIds;
import com.lifeos.strategy.audit.entity.AuditAction;
import com.lifeos.strategy.audit.entity.AuditActor;
import com.lifeos.strategy.audit.entity.AuditEntry;
import com.lifeos.strategy.audit.repository.AuditEntryRepository;
import com.lifeos.strategy.audit.service.AuditService;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditServiceImpl implements AuditService {

    private final AuditEntryRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AuditServiceImpl(AuditEntryRepository repository, JsonMapper jsonMapper, Clock clock) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(UUID userId, AuditAction action, AuditActor actor, Map<String, ?> details) {
        repository.save(AuditEntry.of(userId, action, actor, MDC.get(CorrelationIds.MDC_KEY),
                jsonMapper.writeValueAsString(details), clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean wasRecorded(UUID userId, AuditAction action) {
        return repository.existsByUserIdAndAction(userId, action);
    }
}

package com.lifeos.strategy.account.service.impl;

import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.strategy.account.client.IdentityAccounts;
import com.lifeos.strategy.account.client.IdentityAccounts.IdentityDeletion;
import com.lifeos.strategy.account.event.UserDeletionRequestedV1;
import com.lifeos.strategy.account.repository.UserDataRepository;
import com.lifeos.strategy.account.service.AccountDeletionService;
import com.lifeos.strategy.audit.entity.AuditAction;
import com.lifeos.strategy.audit.entity.AuditActor;
import com.lifeos.strategy.audit.service.AuditService;
import com.lifeos.strategy.profile.repository.UserProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One transaction: lock the profile, delete every table's rows, write the audit record, publish the event. The
 * audit record also serves as the tombstone that keeps a still-valid token from recreating the profile.
 */
@Service
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private final UserDataRepository userDataRepository;
    private final UserProfileRepository userProfileRepository;
    private final IdentityAccounts identityAccounts;
    private final AuditService auditService;
    private final Outbox outbox;
    private final Clock clock;

    public AccountDeletionServiceImpl(UserDataRepository userDataRepository,
            UserProfileRepository userProfileRepository, IdentityAccounts identityAccounts,
            AuditService auditService, Outbox outbox, Clock clock) {
        this.userDataRepository = userDataRepository;
        this.userProfileRepository = userProfileRepository;
        this.identityAccounts = identityAccounts;
        this.auditService = auditService;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void delete(UUID userId) {
        // Requests of the same user that lock the profile (e.g. activating an idea) run before or after, not during.
        userProfileRepository.findForUpdate(userId);
        Map<String, Integer> deleted = userDataRepository.deleteAll(userId);
        Instant now = clock.instant();
        auditService.record(userId, AuditAction.USER_DATA_DELETED, AuditActor.USER, Map.of("rows", deleted));
        // After the outbox rows were deleted, so this event is the one that stays.
        outbox.append(new OutboxMessage("User", userId, userId, UserDeletionRequestedV1.TYPE,
                UserDeletionRequestedV1.VERSION, new UserDeletionRequestedV1(now)));
    }

    @Override
    @Transactional
    public void deleteIdentity(UUID userId) {
        IdentityDeletion result = identityAccounts.delete(userId);
        auditService.record(userId, AuditAction.IDENTITY_ACCOUNT_DELETED, AuditActor.SYSTEM,
                Map.of("result", result.name()));
    }

    @Override
    @Transactional
    public void recordIdentityDeletionFailed(UUID userId) {
        auditService.record(userId, AuditAction.IDENTITY_ACCOUNT_DELETION_FAILED, AuditActor.SYSTEM, Map.of());
    }
}

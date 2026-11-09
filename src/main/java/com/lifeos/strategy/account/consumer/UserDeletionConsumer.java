package com.lifeos.strategy.account.consumer;

import com.lifeos.base.messaging.EventEnvelope;
import com.lifeos.base.messaging.EventEnvelopeCodec;
import com.lifeos.base.messaging.MalformedEventException;
import com.lifeos.base.messaging.inbox.Inbox;
import com.lifeos.strategy.account.event.UserDeletionRequestedV1;
import com.lifeos.strategy.account.service.AccountDeletionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/**
 * Deletes the identity account after Strategy published {@code UserDeletionRequested} (its own event, read back
 * from its own topic): the deletion request never waits for Keycloak, and an unreachable Keycloak is retried
 * (default: 8 attempts a minute apart) instead of leaving the account behind. When every attempt failed, the
 * dead-letter handler records the failure in the audit log for a person to finish.
 */
@Component
public class UserDeletionConsumer {

    /** Inbox consumer name: Strategy's other consumer ("strategy") must not share de-duplication with this one. */
    static final String INBOX_CONSUMER = "strategy.identity";

    private static final Logger log = LoggerFactory.getLogger(UserDeletionConsumer.class);

    private final Inbox inbox;
    private final EventEnvelopeCodec eventEnvelopeCodec;
    private final AccountDeletionService accountDeletionService;

    public UserDeletionConsumer(Inbox inbox, EventEnvelopeCodec eventEnvelopeCodec,
            AccountDeletionService accountDeletionService) {
        this.inbox = inbox;
        this.eventEnvelopeCodec = eventEnvelopeCodec;
        this.accountDeletionService = accountDeletionService;
    }

    // Own retry and dead-letter topics: Trigger also consumes this topic with retries, and its dead letters must
    // not reach this handler.
    @RetryableTopic(exclude = MalformedEventException.class,
            attempts = "${lifeos.strategy.identity.deletion-attempts:8}",
            backOff = @BackOff(delayString = "${lifeos.strategy.identity.retry-delay-ms:60000}"),
            retryTopicSuffix = "-strategy-identity-retry", dltTopicSuffix = "-strategy-identity-dlt")
    @KafkaListener(topics = "${lifeos.messaging.outbox.topic}", groupId = "strategy-identity")
    public void on(ConsumerRecord<String, String> record) {
        inbox.handle(INBOX_CONSUMER, record.value(), event -> {
            if (event.is(UserDeletionRequestedV1.TYPE, UserDeletionRequestedV1.VERSION)) {
                accountDeletionService.deleteIdentity(event.userId());
            }
        });
    }

    /** Every attempt failed, or the event was malformed. Only a failed account deletion is audited. */
    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        EventEnvelope envelope;
        try {
            envelope = eventEnvelopeCodec.fromJson(record.value());
        } catch (MalformedEventException e) {
            log.error("Malformed event on the dead-letter topic {} at offset {}", record.topic(), record.offset(), e);
            return;
        }
        if (UserDeletionRequestedV1.TYPE.equals(envelope.eventType())) {
            log.error("Could not delete the identity account of user {}; recorded for manual follow-up",
                    envelope.userId());
            accountDeletionService.recordIdentityDeletionFailed(envelope.userId());
        } else {
            log.error("Event {} ({}) ended on the dead-letter topic", envelope.eventId(), envelope.eventType());
        }
    }
}

package com.lifeos.strategy.decision.consumer;

import com.lifeos.base.messaging.MalformedEventException;
import com.lifeos.base.messaging.inbox.Inbox;
import com.lifeos.strategy.decision.event.ReopenTriggerFiredV1;
import com.lifeos.strategy.decision.service.DecisionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/**
 * Strategy's consumer of Trigger's events: a fired reopen trigger is recorded on the decision. Each event once
 * (inbox); the others (reminders, drift) are for Notification and Coach, and are ignored here.
 */
@Component
public class TriggerEventConsumer {

    private final Inbox inbox;
    private final DecisionService decisionService;

    public TriggerEventConsumer(Inbox inbox, DecisionService decisionService) {
        this.inbox = inbox;
        this.decisionService = decisionService;
    }

    // Own retry and dead-letter topics, apart from other consumers of Trigger's topic.
    @RetryableTopic(exclude = MalformedEventException.class, retryTopicSuffix = "-strategy-retry",
            dltTopicSuffix = "-strategy-dlt")
    @KafkaListener(topics = "${lifeos.strategy.trigger-topic:lifeos.trigger.events.v1}", groupId = "strategy")
    public void on(ConsumerRecord<String, String> record) {
        inbox.handle(record, event -> {
            if (event.is(ReopenTriggerFiredV1.TYPE, ReopenTriggerFiredV1.VERSION)) {
                ReopenTriggerFiredV1 fired = event.payloadAs(ReopenTriggerFiredV1.class);
                decisionService.reopenTriggerFired(event.userId(), fired.decisionId(), fired.position(),
                        fired.dueAt());
            }
        });
    }
}

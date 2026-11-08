package com.lifeos.strategy.proposal.consumer;

import com.lifeos.base.messaging.MalformedEventException;
import com.lifeos.base.messaging.inbox.Inbox;
import com.lifeos.strategy.proposal.event.RecommendationProposedV1;
import com.lifeos.strategy.proposal.service.ChangeProposalService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/**
 * Strategy's consumer of Coach's events. Each event is handled once ({@link Inbox}, in one transaction with the
 * handler's writes); event types Strategy does not use are marked processed and ignored. A failing handler is
 * retried on the retry topics; a malformed event goes straight to the dead-letter topic.
 */
@Component
public class CoachEventConsumer {

    private final Inbox inbox;
    private final ChangeProposalService changeProposalService;

    public CoachEventConsumer(Inbox inbox, ChangeProposalService changeProposalService) {
        this.inbox = inbox;
        this.changeProposalService = changeProposalService;
    }

    @RetryableTopic(exclude = MalformedEventException.class)
    @KafkaListener(topics = "${lifeos.strategy.coach-topic:lifeos.coach.events.v1}", groupId = "strategy")
    public void on(ConsumerRecord<String, String> record) {
        inbox.handle(record, event -> {
            if (event.is(RecommendationProposedV1.TYPE, RecommendationProposedV1.VERSION)) {
                changeProposalService.receive(event.userId(), event.payloadAs(RecommendationProposedV1.class));
            }
        });
    }
}

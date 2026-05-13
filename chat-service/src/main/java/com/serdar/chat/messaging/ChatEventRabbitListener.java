package com.serdar.chat.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import com.serdar.chat.service.EventBroker;
import com.serdar.proto.chat.ChatEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat-events.rabbit-enabled", havingValue = "true")
public class ChatEventRabbitListener {

    private final EventBroker broker;

    @RabbitListener(queues = "#{chatEventsQueue.name}")
    public void onEvent(ChatEventEnvelope envelope) {
        try {
            broker.deliverRemote(envelope.userId(), ChatEvent.parseFrom(envelope.eventBytes()), envelope.originId());
        } catch (InvalidProtocolBufferException e) {
            log.warn("Dropped malformed chat event from RabbitMQ: {}", e.getMessage());
        }
    }
}

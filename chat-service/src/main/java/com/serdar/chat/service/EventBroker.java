package com.serdar.chat.service;

import com.serdar.chat.messaging.ChatEventEnvelope;
import com.serdar.chat.messaging.ChatEventRabbitConfig;
import com.serdar.proto.chat.ChatEvent;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user event fan-out for chat streams.
 *
 * Local subscribers are served in-memory. When Rabbit fan-out is enabled,
 * committed chat events are also published to a shared exchange so another
 * chat-service instance can deliver the same event to its own local streams.
 */
@Component
public class EventBroker {

    private static final Logger log = LoggerFactory.getLogger(EventBroker.class);

    private final Map<Long, Set<StreamObserver<ChatEvent>>> subs = new ConcurrentHashMap<>();
    private final Map<Long, Integer> sessionCounts = new ConcurrentHashMap<>();
    private final RabbitTemplate rabbit;
    private final boolean rabbitEnabled;
    private final String instanceId = UUID.randomUUID().toString();

    public EventBroker(
            ObjectProvider<RabbitTemplate> rabbit,
            @Value("${app.chat-events.rabbit-enabled}") boolean rabbitEnabled
    ) {
        this.rabbit = rabbit.getIfAvailable();
        this.rabbitEnabled = rabbitEnabled;
    }

    public void subscribe(long userId, StreamObserver<ChatEvent> sub) {
        subs.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sub);
        int sessions = sessionCounts.merge(userId, 1, Integer::sum);
        log.debug("user {} subscribed ({} sessions)", userId, sessions);
    }

    public void unsubscribe(long userId, StreamObserver<ChatEvent> sub) {
        Set<StreamObserver<ChatEvent>> s = subs.get(userId);
        if (s != null) {
            s.remove(sub);
            if (s.isEmpty()) subs.remove(userId);
        }
        Integer n = sessionCounts.computeIfPresent(userId, (k, v) -> v <= 1 ? null : v - 1);
        log.debug("user {} unsubscribed ({} sessions left)", userId, n == null ? 0 : n);
    }

    public boolean isOnline(long userId) {
        return subs.containsKey(userId);
    }

    public void sendTo(long userId, ChatEvent event) {
        deliverLocal(userId, event);
        publishRemote(userId, event);
    }

    public void deliverRemote(long userId, ChatEvent event, String originId) {
        if (instanceId.equals(originId)) return;
        deliverLocal(userId, event);
    }

    private void deliverLocal(long userId, ChatEvent event) {
        Set<StreamObserver<ChatEvent>> s = subs.get(userId);
        if (s == null) return;
        for (Iterator<StreamObserver<ChatEvent>> it = s.iterator(); it.hasNext(); ) {
            StreamObserver<ChatEvent> sub = it.next();
            try {
                sub.onNext(event);
            } catch (Exception e) {
                log.warn("drop subscriber for {}: {}", userId, e.getMessage());
                it.remove();
            }
        }
    }

    private void publishRemote(long userId, ChatEvent event) {
        if (!rabbitEnabled) return;
        if (rabbit == null) {
            log.warn("Rabbit chat event fan-out is enabled but no RabbitTemplate is available");
            return;
        }
        try {
            rabbit.convertAndSend(
                    ChatEventRabbitConfig.EXCHANGE,
                    "",
                    new ChatEventEnvelope(userId, event.toByteArray(), instanceId)
            );
        } catch (Exception e) {
            log.warn("Could not publish chat event to RabbitMQ: {}", e.getMessage());
        }
    }
}

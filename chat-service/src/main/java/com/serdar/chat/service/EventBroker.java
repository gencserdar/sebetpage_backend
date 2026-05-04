package com.serdar.chat.service;

import com.serdar.proto.chat.ChatEvent;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fan-out for per-user event streams. The gateway opens a
 * {@code SubscribeEvents} stream for each connected WebSocket user and this
 * class routes {@link ChatEvent}s to those subscribers.
 *
 * This is intentionally simple — for a multi-instance deployment you'd back
 * it with Redis pub/sub or a message broker; the interface stays the same.
 */
@Component
public class EventBroker {

    private static final Logger log = LoggerFactory.getLogger(EventBroker.class);

    private final Map<Long, Set<StreamObserver<ChatEvent>>> subs = new ConcurrentHashMap<>();
    private final Map<Long, Integer> sessionCounts = new ConcurrentHashMap<>();

    public void subscribe(long userId, StreamObserver<ChatEvent> sub) {
        subs.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sub);
        int sessions = sessionCounts.merge(userId, 1, Integer::sum);
        log.debug("user {} subscribed ({} sessions)", userId, sessions);
    }

    public void unsubscribe(long userId, StreamObserver<ChatEvent> sub) {
        Set<StreamObserver<ChatEvent>> s = subs.get(userId);
        if (s != null) { s.remove(sub); if (s.isEmpty()) subs.remove(userId); }
        Integer n = sessionCounts.computeIfPresent(userId, (k, v) -> v <= 1 ? null : v - 1);
        log.debug("user {} unsubscribed ({} sessions left)", userId, n == null ? 0 : n);
    }

    public boolean isOnline(long userId) {
        return subs.containsKey(userId);
    }

    public void sendTo(long userId, ChatEvent event) {
        Set<StreamObserver<ChatEvent>> s = subs.get(userId);
        if (s == null) return;
        for (Iterator<StreamObserver<ChatEvent>> it = s.iterator(); it.hasNext(); ) {
            StreamObserver<ChatEvent> sub = it.next();
            try { sub.onNext(event); }
            catch (Exception e) {
                log.warn("drop subscriber for {}: {}", userId, e.getMessage());
                it.remove();
            }
        }
    }
}

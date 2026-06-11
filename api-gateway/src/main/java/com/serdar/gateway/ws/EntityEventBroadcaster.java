package com.serdar.gateway.ws;

import com.serdar.common.grpc.GrpcActorContext;
import com.serdar.gateway.client.ChatClient;
import com.serdar.gateway.client.UserClient;
import com.serdar.proto.chat.ChatEvent;
import com.serdar.proto.user.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Push entity-level "something changed" notifications to the right audience.
 *
 * Event shape — kept generic so adding new entity types later is mechanical:
 * <pre>
 *   {
 *     "type"      : "USER_UPDATED",         // also: "GROUP_UPDATED", "POST_UPDATED", ...
 *     "entityType": "USER",
 *     "entity"    : { id, ...fields }       // canonical row to overwrite client cache with
 *   }
 * </pre>
 *
 * Today there's only userUpdated. To add a new entity, write a new method that
 * (1) builds the entity payload, (2) decides the audience (group members, post
 * followers, etc.), (3) calls {@link #send(long, Map)} for each member and
 * for the subject themselves (cross-tab sync).
 *
 * Destination is /user/queue/friends — the frontend already has a STOMP
 * subscription there for friend / presence events, so reusing it avoids
 * standing up a parallel subscription. If post/feed traffic gets too noisy
 * later, split into /queue/users, /queue/groups, etc.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityEventBroadcaster {

    private static final String DESTINATION = "/queue/friends";

    private final SimpMessagingTemplate stomp;
    private final UserClient users;
    private final ChatClient chat;

    /**
     * Publish a USER_UPDATED event to the user themselves (cross-tab sync)
     * and every friend (so their cached references / friend lists / open
     * chat headers refresh without a roundtrip).
     */
    public void userUpdated(UserProfile updated) {
        if (updated == null || updated.getId() == 0) return;
        long id = updated.getId();

        Map<String, Object> entity = userToMap(updated);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "USER_UPDATED");
        event.put("entityType", "USER");
        event.put("entity", entity);

        // Self first — covers the case of the user being logged in on
        // multiple tabs and renaming themselves in tab 1.
        send(id, event);

        Set<Long> audience = new LinkedHashSet<>();
        try {
            ChatEvent snapshot = GrpcActorContext.callAs(id, () -> chat.getPresenceSnapshot(id));
            snapshot.getPresenceSnapshotList().forEach(entry -> audience.add(entry.getUserId()));
        } catch (Exception e) {
            log.warn("USER_UPDATED presence audience fetch failed for {}: {}", id, e.getMessage());
        }
        if (audience.isEmpty()) {
            try {
                audience.addAll(users.friendIds(id));
            } catch (Exception e) {
                log.warn("USER_UPDATED friend audience fetch failed for {}: {}", id, e.getMessage());
                return;
            }
        }
        for (long recipientId : audience) {
            if (recipientId == id) continue;
            send(recipientId, event);
        }
    }

    /** Single-recipient send. Kept private — entity-specific public methods
     *  decide the audience; downstream code shouldn't address users directly. */
    private void send(long userId, Map<String, Object> event) {
        stomp.convertAndSendToUser(String.valueOf(userId), DESTINATION, event);
    }

    private static Map<String, Object> userToMap(UserProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("email", p.getEmail());
        m.put("nickname", p.getNickname());
        m.put("name", p.getName());
        m.put("surname", p.getSurname());
        m.put("profileImageUrl", p.getProfileImageUrl());
        return m;
    }
}

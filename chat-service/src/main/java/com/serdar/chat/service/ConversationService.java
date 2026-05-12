package com.serdar.chat.service;

import com.serdar.chat.client.UserClient;
import com.serdar.chat.entity.Conversation;
import com.serdar.chat.entity.ConversationParticipant;
import com.serdar.chat.repository.ConversationParticipantRepository;
import com.serdar.chat.repository.ConversationRepository;
import com.serdar.common.ServiceException;
import com.serdar.proto.chat.ChatEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversations;
    private final ConversationParticipantRepository participants;
    private final UserClient userClient;
    private final EventBroker broker;

    /**
     * Canonicalise direct conversations so (a,b) and (b,a) hit the same row.
     *
     * Race-safety: the conversations table has a unique constraint on
     * (type, user_a_id, user_b_id). Two concurrent calls for the same pair
     * can both pass the initial findBy check and then race to insert. The
     * loser gets a DataIntegrityViolationException; we catch it and fall back
     * to a second findBy, which is now guaranteed to succeed.
     */
    @Transactional
    public Conversation getOrCreateDirect(long u1, long u2) {
        long a = Math.min(u1, u2), b = Math.max(u1, u2);

        var existing = conversations.findByTypeAndUserAIdAndUserBId(Conversation.Type.DIRECT, a, b);
        if (existing.isPresent()) return existing.get();

        try {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            Conversation c = conversations.save(Conversation.builder()
                    .type(Conversation.Type.DIRECT)
                    .userAId(a).userBId(b)
                    .createdAt(now)
                    .build());
            participants.save(ConversationParticipant.builder()
                    .conversationId(c.getId()).userId(a).joinedAt(now).build());
            participants.save(ConversationParticipant.builder()
                    .conversationId(c.getId()).userId(b).joinedAt(now).build());
            return c;
        } catch (DataIntegrityViolationException ex) {
            return conversations.findByTypeAndUserAIdAndUserBId(Conversation.Type.DIRECT, a, b)
                    .orElseThrow(() -> new IllegalStateException(
                            "Unique constraint fired but row not found for direct conversation (" + a + "," + b + ")", ex));
        }
    }

    /**
     * Create a private messaging group from an existing direct chat.
     *
     * The creator is always ADMIN. All additional members must be friends with
     * the creator; the frontend filters to friends too, but this server-side
     * check keeps the API honest if someone calls it directly.
     */
    @Transactional
    public Conversation createMessagingGroup(long creatorId, List<Long> memberIds, String name) {
        Set<Long> validMemberIds = validatedFriendMemberIds(creatorId, memberIds);
        if (validMemberIds.isEmpty()) {
            throw ServiceException.invalid("A messaging group needs at least one other member");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Conversation c = conversations.save(Conversation.builder()
                .type(Conversation.Type.MESSAGING_GROUP)
                .title(name != null && !name.isBlank() ? name.trim() : null)
                .createdById(creatorId)
                .createdAt(now)
                .build());

        participants.save(ConversationParticipant.builder()
                .conversationId(c.getId()).userId(creatorId)
                .joinedAt(now).role("ADMIN")
                .canChangePhoto(true)
                .canChangeDescription(true)
                .canChangeName(true)
                .canRemoveMembers(true)
                .canAddMembers(true)
                .build());

        validMemberIds.forEach(id -> participants.save(ConversationParticipant.builder()
                .conversationId(c.getId()).userId(id)
                .joinedAt(now).role("MEMBER").build()));

        Set<Long> notifyUserIds = new LinkedHashSet<>();
        notifyUserIds.add(creatorId);
        notifyUserIds.addAll(validMemberIds);
        notifyMessagingGroupAdded(c.getId(), notifyUserIds);

        return c;
    }

    private Set<Long> validatedFriendMemberIds(long creatorId, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return Set.of();

        Set<Long> friendIds = Set.copyOf(userClient.friendIds(creatorId));
        Set<Long> valid = new LinkedHashSet<>();
        for (Long id : memberIds) {
            if (id == null || id == creatorId) continue;
            if (!friendIds.contains(id)) {
                throw ServiceException.forbidden("Only friends can be added to messaging groups");
            }
            if (userClient.isBlockedEitherWay(creatorId, id)) {
                throw ServiceException.forbidden("Blocked");
            }
            valid.add(id);
        }
        return valid;
    }

    private void notifyMessagingGroupAdded(long conversationId, Set<Long> userIds) {
        Runnable send = () -> {
            ChatEvent event = ChatEvent.newBuilder()
                    .setType("MESSAGING_GROUP_ADDED")
                    .setConversationId(conversationId)
                    .build();
            userIds.forEach(id -> broker.sendTo(id, event));
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}

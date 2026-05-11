package com.serdar.chat.service;

import com.serdar.chat.client.UserClient;
import com.serdar.chat.entity.Conversation;
import com.serdar.chat.entity.ConversationParticipant;
import com.serdar.chat.entity.Message;
import com.serdar.chat.repository.ConversationParticipantRepository;
import com.serdar.chat.repository.ConversationRepository;
import com.serdar.chat.repository.MessageRepository;
import com.serdar.common.AesGcm;
import com.serdar.common.ServiceException;
import com.serdar.proto.chat.ChatEvent;
import com.serdar.proto.chat.PresenceEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatDomainService {

    private final ConversationRepository conversations;
    private final ConversationParticipantRepository participants;
    private final MessageRepository messages;
    private final AesGcm aes;
    private final UserClient userClient;
    private final EventBroker broker;

    // --- send / read messages -----------------------------------------------

    @Transactional
    public Message send(long conversationId, long senderId, String plaintext) {
        Conversation c = conversations.findById(conversationId)
                .orElseThrow(() -> ServiceException.notFound("Conversation not found"));
        assertActiveMember(conversationId, senderId);
        // 1-1 direct conversations: check block state with the other participant.
        if (c.getType() == Conversation.Type.DIRECT) {
            long other = c.getUserAId().equals(senderId) ? c.getUserBId() : c.getUserAId();
            if (userClient.isBlockedEitherWay(senderId, other))
                throw ServiceException.forbidden("Blocked");
        }
        AesGcm.Enc enc = aes.encrypt(plaintext, AesGcm.aad(conversationId, senderId));
        Message m = messages.save(Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .contentCipherB64(enc.cipherB64())
                .contentIvB64(enc.ivB64())
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());

        broadcastMessage(c, m, plaintext);
        return m;
    }

    private void broadcastMessage(Conversation c, Message m, String plaintext) {
        com.serdar.proto.chat.ChatMessage msg = toProtoMessage(m, plaintext);
        for (ConversationParticipant p : participants.findByConversationIdAndDeletedAtIsNull(c.getId())) {
            broker.sendTo(p.getUserId(),
                    ChatEvent.newBuilder()
                            .setType("MESSAGE")
                            .setConversationId(c.getId())
                            .setMessage(msg)
                            .build());
            // unread count bump for everyone other than the sender
            if (p.getUserId() != m.getSenderId()) {
                long unread = messages.countUnreadFor(c.getId(), p.getUserId(), p.getLastReadAt());
                broker.sendTo(p.getUserId(),
                        ChatEvent.newBuilder()
                                .setType("UNREAD_COUNT_UPDATE")
                                .setConversationId(c.getId())
                                .setUnreadCount((int) unread)
                                .build());
            }
        }
    }

    public List<com.serdar.proto.chat.ChatMessage> getLatest(long conversationId, long callerId, int limit) {
        assertMember(conversationId, callerId);
        Pageable page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        Page<Message> desc = messages.findByConversationIdOrderByCreatedAtDesc(conversationId, page);
        List<Message> asc = new ArrayList<>(desc.getContent());
        Collections.reverse(asc);
        return asc.stream().map(this::decrypt).toList();
    }

    public Page<com.serdar.proto.chat.ChatMessage> getPage(long conversationId, long callerId, int page, int size) {
        assertMember(conversationId, callerId);
        Pageable p = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)));
        return messages.findByConversationIdOrderByCreatedAtDesc(conversationId, p).map(this::decrypt);
    }

    private com.serdar.proto.chat.ChatMessage decrypt(Message m) {
        String plain = aes.decrypt(m.getContentIvB64(), m.getContentCipherB64(),
                AesGcm.aad(m.getConversationId(), m.getSenderId()));
        return toProtoMessage(m, plain);
    }

    // --- reads / unread -----------------------------------------------------

    @Transactional
    public MarkReadResult markRead(long conversationId, long readerId) {
        Conversation c = conversations.findById(conversationId)
                .orElseThrow(() -> ServiceException.notFound("Conversation not found"));
        ConversationParticipant me = participants.findByConversationIdAndUserId(conversationId, readerId)
                .orElseThrow(() -> ServiceException.forbidden("Not a participant"));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        me.setLastReadAt(now);
        participants.save(me);

        long unread = messages.countUnreadFor(conversationId, readerId, now);
        int totalUnread = totalUnreadFor(readerId);

        ChatEvent readEvt = ChatEvent.newBuilder()
                .setType("READ")
                .setConversationId(conversationId)
                .setReaderId(readerId)
                .setLastReadAtMillis(now.toInstant(ZoneOffset.UTC).toEpochMilli())
                .build();
        // Broadcast READ to every participant, and refresh the reader's own unread widget.
        for (ConversationParticipant p : participants.findByConversationIdAndDeletedAtIsNull(conversationId)) {
            broker.sendTo(p.getUserId(), readEvt);
        }
        broker.sendTo(readerId,
                ChatEvent.newBuilder()
                        .setType("UNREAD_COUNT_UPDATE")
                        .setConversationId(conversationId)
                        .setUnreadCount((int) unread)
                        .setTotalUnreadCount(totalUnread)
                        .build());

        return new MarkReadResult((int) unread, totalUnread, now);
    }

    public int totalUnreadFor(long userId) {
        int total = 0;
        for (ConversationParticipant p : participants.findByUserIdAndDeletedAtIsNull(userId)) {
            total += (int) messages.countUnreadFor(p.getConversationId(), userId, p.getLastReadAt());
        }
        return total;
    }

    public UnreadCounts unreadCounts(long userId) {
        int total = 0;
        Map<Long, Integer> per = new HashMap<>();
        for (ConversationParticipant p : participants.findByUserIdAndDeletedAtIsNull(userId)) {
            int n = (int) messages.countUnreadFor(p.getConversationId(), userId, p.getLastReadAt());
            total += n;
            per.put(p.getConversationId(), n);
        }
        return new UnreadCounts(total, per);
    }

    public ReadState readState(long conversationId, long callerId) {
        Conversation c = conversations.findById(conversationId)
                .orElseThrow(() -> ServiceException.notFound("Conversation not found"));
        if (c.getType() != Conversation.Type.DIRECT)
            return new ReadState(null, null, null, 0, callerId);
        long friendId = c.getUserAId().equals(callerId) ? c.getUserBId() : c.getUserAId();

        ConversationParticipant me     = participants.findByConversationIdAndUserId(conversationId, callerId).orElse(null);
        ConversationParticipant friend = participants.findByConversationIdAndUserId(conversationId, friendId).orElse(null);

        Long seenMessageId = null;
        if (friend != null && friend.getLastReadAt() != null) {
            var hits = messages.lastFromSenderBefore(conversationId, callerId, friend.getLastReadAt(),
                    PageRequest.of(0, 1));
            if (!hits.isEmpty()) seenMessageId = hits.get(0).getId();
        }
        return new ReadState(
                me == null ? null : me.getLastReadAt(),
                friend == null ? null : friend.getLastReadAt(),
                seenMessageId, friendId, callerId);
    }

    public Conversation getConversation(long id) {
        return conversations.findById(id).orElseThrow(() -> ServiceException.notFound("Conversation not found"));
    }

    public List<Conversation> myConversations(long userId) {
        return participants.findByUserIdAndDeletedAtIsNull(userId).stream()
                .map(p -> conversations.findById(p.getConversationId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    // --- presence -----------------------------------------------------------

    public ChatEvent presenceSnapshotFor(long userId) {
        ChatEvent.Builder b = ChatEvent.newBuilder().setType("PRESENCE_SNAPSHOT");
        for (long fid : userClient.friendIds(userId)) {
            b.addPresenceSnapshot(PresenceEntry.newBuilder().setUserId(fid).setOnline(broker.isOnline(fid)));
        }
        return b.build();
    }

    public void broadcastPresence(long userId, boolean online) {
        ChatEvent evt = ChatEvent.newBuilder()
                .setType("PRESENCE_UPDATE")
                .setSubjectUserId(userId)
                .setOnline(online)
                .build();
        for (long fid : userClient.friendIds(userId)) broker.sendTo(fid, evt);
    }

    // --- helpers ------------------------------------------------------------

    private void assertMember(long conversationId, long userId) {
        participants.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ServiceException.forbidden("Not a participant"));
    }

    private void assertActiveMember(long conversationId, long userId) {
        participants.findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(() -> ServiceException.forbidden("Not a participant"));
    }

    private com.serdar.proto.chat.ChatMessage toProtoMessage(Message m, String plaintext) {
        return com.serdar.proto.chat.ChatMessage.newBuilder()
                .setId(m.getId())
                .setConversationId(m.getConversationId())
                .setSenderId(m.getSenderId())
                .setContent(plaintext)
                .setCreatedAtMillis(m.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli())
                .build();
    }

    public record MarkReadResult(int unread, int totalUnread, LocalDateTime lastReadAt) {}
    public record UnreadCounts(int total, Map<Long, Integer> perConversation) {}
    public record ReadState(LocalDateTime myLastReadAt, LocalDateTime friendLastReadAt,
                            Long seenMyMessageId, long friendUserId, long myUserId) {}
}

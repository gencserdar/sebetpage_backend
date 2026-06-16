package com.serdar.chat.service;

import com.serdar.chat.client.AuthClient;
import com.serdar.chat.client.UserClient;
import com.serdar.chat.config.ChatLimits;
import com.serdar.chat.cache.UnreadCacheService;
import com.serdar.chat.entity.Conversation;
import com.serdar.chat.entity.ConversationParticipant;
import com.serdar.chat.model.Message;
import com.serdar.chat.repository.ConversationParticipantRepository;
import com.serdar.chat.repository.ConversationRepository;
import com.serdar.chat.repository.MessageStore;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChatDomainService {

    private final ConversationRepository conversations;
    private final ConversationParticipantRepository participants;
    private final MessageStore messages;
    private final UnreadCacheService unreadCache;
    private final AesGcm aes;
    private final UserClient userClient;
    private final AuthClient authClient;
    private final EventBroker broker;
    private final ChatLimits limits;

    // --- send / read messages -----------------------------------------------

    @Transactional
    public Message send(long conversationId, long senderId, String plaintext) {
        if (authClient.isFrozen(senderId)) {
            throw ServiceException.forbidden("Account frozen");
        }
        String content = limits.requireValidMessage(plaintext);
        Conversation c = conversations.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> ServiceException.notFound("Conversation not found"));
        assertActiveMember(conversationId, senderId);
        // 1-1 direct conversations: check block state with the other participant.
        if (c.getType() == Conversation.Type.DIRECT) {
            long other = c.getUserAId().equals(senderId) ? c.getUserBId() : c.getUserAId();
            if (userClient.isBlockedEitherWay(senderId, other))
                throw ServiceException.forbidden("Blocked");
        }
        AesGcm.Enc enc = aes.encrypt(content, AesGcm.aad(conversationId, senderId));
        Message m = messages.save(Message.builder()
                .id(MessageIdGenerator.nextId())
                .conversationId(conversationId)
                .senderId(senderId)
                .contentCipherB64(enc.cipherB64())
                .contentIvB64(enc.ivB64())
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());

        broadcastMessage(c, m, content);
        return m;
    }

    private void broadcastMessage(Conversation c, Message m, String plaintext) {
        com.serdar.proto.chat.ChatMessage msg = toProtoMessage(m, plaintext);
        boolean group = c.getType() == Conversation.Type.MESSAGING_GROUP;
        for (ConversationParticipant p : participants.findByConversationIdAndDeletedAtIsNull(c.getId())) {
            if (group && shouldHideGroupMessageFrom(p.getUserId(), m.getSenderId())) {
                continue;
            }
            broker.sendTo(p.getUserId(),
                    ChatEvent.newBuilder()
                            .setType("MESSAGE")
                            .setConversationId(c.getId())
                            .setMessage(msg)
                            .build());
            // unread count bump for everyone other than the sender, unless they muted the chat.
            // System messages (senderId <= 0) never affect unread badges.
            if (p.getUserId() != m.getSenderId()
                    && !Boolean.TRUE.equals(p.getMuted())
                    && m.getSenderId() != null
                    && m.getSenderId() > 0) {
                if (!group || !shouldHideGroupMessageFrom(p.getUserId(), m.getSenderId())) {
                    unreadCache.increment(p.getUserId(), c.getId());
                }
                int unread = unreadCache.getConversationUnread(
                        p.getUserId(), c.getId(),
                        () -> (int) countVisibleUnread(c, p.getUserId(), p.getLastReadAt()));
                broker.sendTo(p.getUserId(),
                        ChatEvent.newBuilder()
                                .setType("UNREAD_COUNT_UPDATE")
                                .setConversationId(c.getId())
                                .setUnreadCount(unread)
                                .build());
            }
        }
    }

    public List<com.serdar.proto.chat.ChatMessage> getLatest(long conversationId, long callerId, int limit) {
        assertActiveMember(conversationId, callerId);
        Conversation c = conversations.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> ServiceException.notFound("Conversation not found"));
        int capped = Math.max(1, Math.min(limit, 200));
        if (!isMessagingGroup(c)) {
            Page<Message> desc = messages.findByConversationIdOrderByCreatedAtDesc(
                    conversationId, PageRequest.of(0, capped));
            List<Message> asc = new ArrayList<>(desc.getContent());
            Collections.reverse(asc);
            return asc.stream().map(this::decrypt).toList();
        }
        Set<Long> hidden = userClient.blockedByMeIds(callerId);
        List<com.serdar.proto.chat.ChatMessage> result = new ArrayList<>();
        int dbPage = 0;
        int chunk = Math.max(capped, 50);
        while (result.size() < capped) {
            Page<Message> desc = messages.findByConversationIdOrderByCreatedAtDesc(
                    conversationId, PageRequest.of(dbPage++, chunk));
            if (desc.isEmpty()) break;
            List<Message> asc = new ArrayList<>(desc.getContent());
            Collections.reverse(asc);
            for (Message m : asc) {
                if (hidden.contains(m.getSenderId())) continue;
                result.add(decrypt(m));
                if (result.size() >= capped) break;
            }
            if (desc.isLast()) break;
        }
        return result;
    }

    public Page<com.serdar.proto.chat.ChatMessage> getPage(long conversationId, long callerId, int page, int size) {
        assertActiveMember(conversationId, callerId);
        Conversation c = conversations.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> ServiceException.notFound("Conversation not found"));
        int cappedSize = Math.max(1, Math.min(size, 200));
        Pageable p = PageRequest.of(Math.max(0, page), cappedSize);
        if (!isMessagingGroup(c)) {
            return messages.findByConversationIdOrderByCreatedAtDesc(conversationId, p).map(this::decrypt);
        }
        return getVisibleMessagePage(conversationId, callerId, page, cappedSize, p);
    }

    private com.serdar.proto.chat.ChatMessage decrypt(Message m) {
        String plain = aes.decrypt(m.getContentIvB64(), m.getContentCipherB64(),
                AesGcm.aad(m.getConversationId(), m.getSenderId()));
        return toProtoMessage(m, plain);
    }

    // --- reads / unread -----------------------------------------------------

    @Transactional
    public MarkReadResult markRead(long conversationId, long readerId) {
        Conversation c = conversations.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> ServiceException.notFound("Conversation not found"));
        ConversationParticipant me = participants.findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, readerId)
                .orElseThrow(() -> ServiceException.forbidden("Not a participant"));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        me.setLastReadAt(now);
        participants.saveAndFlush(me);

        unreadCache.clearConversation(readerId, conversationId);
        int totalUnread = computeTotalUnreadFromStore(readerId);

        long unread = 0;

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
        return unreadCache.getTotalUnread(userId, () -> computeTotalUnreadFromStore(userId));
    }

    private int computeTotalUnreadFromStore(long userId) {
        int total = 0;
        Set<Long> hidden = userClient.blockedByMeIds(userId);
        for (ConversationParticipant p : participants.findByUserIdAndDeletedAtIsNull(userId)) {
            if (Boolean.TRUE.equals(p.getMuted())) continue;
            total += visibleUnreadForParticipant(p, userId, hidden);
        }
        unreadCache.warmTotal(userId, total);
        return total;
    }

    public UnreadCounts unreadCounts(long userId) {
        int total = 0;
        Map<Long, Integer> per = new HashMap<>();
        Set<Long> hidden = userClient.blockedByMeIds(userId);
        for (ConversationParticipant p : participants.findByUserIdAndDeletedAtIsNull(userId)) {
            if (Boolean.TRUE.equals(p.getMuted())) {
                per.put(p.getConversationId(), 0);
                continue;
            }
            int n = unreadCache.getConversationUnread(
                    userId, p.getConversationId(),
                    () -> visibleUnreadForParticipant(p, userId, hidden));
            total += n;
            per.put(p.getConversationId(), n);
        }
        unreadCache.warmTotal(userId, total);
        return new UnreadCounts(total, per);
    }

    public ReadState readState(long conversationId, long callerId) {
        assertActiveMember(conversationId, callerId);
        Conversation c = conversations.findByIdAndDeletedAtIsNull(conversationId)
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
        return conversations.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> ServiceException.notFound("Conversation not found"));
    }

    public List<Conversation> myConversations(long userId) {
        return participants.findByUserIdAndDeletedAtIsNull(userId).stream()
                .map(p -> conversations.findByIdAndDeletedAtIsNull(p.getConversationId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(c -> !shouldHideDirectConversation(c, userId))
                .toList();
    }

    private boolean shouldHideDirectConversation(Conversation c, long userId) {
        if (c.getType() != Conversation.Type.DIRECT) return false;
        long other = c.getUserAId().equals(userId) ? c.getUserBId() : c.getUserAId();
        return authClient.isFrozen(other);
    }

    public MessagingGroupDetail messagingGroupDetail(long conversationId, long requesterId) {
        Conversation c = requireMessagingGroup(conversationId);
        ConversationParticipant me = activeParticipant(conversationId, requesterId);
        return new MessagingGroupDetail(
                c,
                participants.findByConversationIdAndDeletedAtIsNull(conversationId),
                me,
                participants.findByConversationId(conversationId)
        );
    }

    @Transactional
    public MessagingGroupDetail updateMessagingGroup(
            long conversationId,
            long requesterId,
            boolean updateTitle,
            String title,
            boolean updateDescription,
            String description,
            boolean updateImageUrl,
            String imageUrl
    ) {
        Conversation c = requireMessagingGroup(conversationId);
        ConversationParticipant requester = activeParticipant(conversationId, requesterId);

        if (updateTitle) {
            requirePermission(c, requester, Permission.CHANGE_NAME);
            c.setTitle(limits.normalizeGroupTitle(title));
        }
        if (updateDescription) {
            requirePermission(c, requester, Permission.CHANGE_DESCRIPTION);
            c.setDescription(limits.normalizeGroupDescription(description));
        }
        if (updateImageUrl) {
            requirePermission(c, requester, Permission.CHANGE_PHOTO);
            c.setImageUrl(validateGroupImageUrl(imageUrl));
        }

        conversations.save(c);
        notifyMessagingGroupEventAfterCommit("MESSAGING_GROUP_UPDATED", conversationId, activeUserIds(conversationId));
        return messagingGroupDetail(conversationId, requesterId);
    }

    @Transactional
    public MessagingGroupDetail updateMessagingGroupParticipant(
            long conversationId,
            long requesterId,
            long targetUserId,
            boolean updateMuted,
            boolean muted,
            boolean updatePermissions,
            PermissionValues permissions
    ) {
        Conversation c = requireMessagingGroup(conversationId);
        ConversationParticipant requester = activeParticipant(conversationId, requesterId);
        ConversationParticipant target = activeParticipant(conversationId, targetUserId);

        if (updateMuted) {
            if (requesterId != targetUserId) {
                throw ServiceException.forbidden("Cannot mute notifications for another member");
            }
            target.setMuted(muted);
        }

        if (updatePermissions) {
            if (targetUserId == requesterId) {
                throw ServiceException.invalid("Cannot change your own permissions");
            }
            applyPermission(c, requester, target, Permission.CHANGE_PHOTO, permissions.canChangePhoto());
            applyPermission(c, requester, target, Permission.CHANGE_DESCRIPTION, permissions.canChangeDescription());
            applyPermission(c, requester, target, Permission.CHANGE_NAME, permissions.canChangeName());
            applyPermission(c, requester, target, Permission.REMOVE_MEMBERS, permissions.canRemoveMembers());
            applyPermission(c, requester, target, Permission.ADD_MEMBERS, permissions.canAddMembers());
        }

        participants.save(target);
        if (updateMuted) {
            int unread = Boolean.TRUE.equals(target.getMuted())
                    ? 0
                    : (int) countVisibleUnread(c, targetUserId, target.getLastReadAt());
            notifyUnreadAfterCommit(targetUserId, conversationId, unread, totalUnreadFor(targetUserId));
        }
        notifyMessagingGroupEventAfterCommit("MESSAGING_GROUP_UPDATED", conversationId, activeUserIds(conversationId));
        return messagingGroupDetail(conversationId, requesterId);
    }

    @Transactional
    public MessagingGroupDetail removeMessagingGroupMember(long conversationId, long requesterId, long targetUserId) {
        Conversation c = requireMessagingGroup(conversationId);
        ConversationParticipant requester = activeParticipant(conversationId, requesterId);
        ConversationParticipant target = activeParticipant(conversationId, targetUserId);
        requirePermission(c, requester, Permission.REMOVE_MEMBERS);
        if (targetUserId == requesterId) {
            throw ServiceException.invalid("Use exit group");
        }
        if (isOwner(c, target)) {
            throw ServiceException.invalid("Cannot remove a group admin");
        }

        target.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        participants.save(target);
        saveSystemMessage(c, userClient.nickname(targetUserId) + " was removed from the group");

        Set<Long> audience = activeUserIds(conversationId);
        notifyMessagingGroupEventAfterCommit("MESSAGING_GROUP_UPDATED", conversationId, audience);
        notifyMessagingGroupEventAfterCommit("MESSAGING_GROUP_LEFT", conversationId, Set.of(targetUserId));
        notifyUnreadAfterCommit(targetUserId, conversationId, 0, totalUnreadFor(targetUserId));
        unreadCache.clearConversation(targetUserId, conversationId);
        return messagingGroupDetail(conversationId, requesterId);
    }

    @Transactional
    public void exitMessagingGroup(long conversationId, long requesterId) {
        Conversation c = requireMessagingGroup(conversationId);
        ConversationParticipant requester = activeParticipant(conversationId, requesterId);
        boolean adminLeft = isOwner(c, requester);
        requester.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        participants.save(requester);
        saveSystemMessage(c, userClient.nickname(requesterId) + " left the group");

        List<ConversationParticipant> remaining = participants.findByConversationIdAndDeletedAtIsNull(conversationId);
        if (remaining.isEmpty()) {
            messages.deleteByConversationId(conversationId);
            participants.deleteByConversationId(conversationId);
            conversations.delete(c);
            notifyMessagingGroupEventAfterCommit("MESSAGING_GROUP_DELETED", conversationId, Set.of(requesterId));
            notifyUnreadAfterCommit(requesterId, conversationId, 0, totalUnreadFor(requesterId));
            unreadCache.clearConversation(requesterId, conversationId);
            return;
        }

        if (adminLeft && remaining.stream().noneMatch(p -> isOwner(c, p))) {
            ConversationParticipant nextAdmin = randomParticipant(remaining);
            grantAdmin(nextAdmin);
            participants.save(nextAdmin);
            saveSystemMessage(c, userClient.nickname(nextAdmin.getUserId()) + " is now group admin");
        }

        notifyMessagingGroupEventAfterCommit("MESSAGING_GROUP_UPDATED", conversationId, activeUserIds(conversationId));
        notifyMessagingGroupEventAfterCommit("MESSAGING_GROUP_LEFT", conversationId, Set.of(requesterId));
        notifyUnreadAfterCommit(requesterId, conversationId, 0, totalUnreadFor(requesterId));
        unreadCache.clearConversation(requesterId, conversationId);
    }

    @Transactional
    public void deleteMessagingGroup(long conversationId, long requesterId) {
        Conversation c = requireMessagingGroup(conversationId);
        ConversationParticipant requester = activeParticipant(conversationId, requesterId);
        if (!isOwner(c, requester)) throw ServiceException.forbidden("Only a group admin can delete this group");
        Set<Long> audience = activeUserIds(conversationId);
        messages.deleteByConversationId(conversationId);
        participants.deleteByConversationId(conversationId);
        conversations.delete(c);
        audience.forEach(userId -> {
            notifyUnreadAfterCommit(userId, conversationId, 0, totalUnreadFor(userId));
            unreadCache.clearConversation(userId, conversationId);
        });
        notifyMessagingGroupEventAfterCommit("MESSAGING_GROUP_DELETED", conversationId, audience);
    }

    // --- presence -----------------------------------------------------------

    public ChatEvent presenceSnapshotFor(long userId) {
        ChatEvent.Builder b = ChatEvent.newBuilder().setType("PRESENCE_SNAPSHOT");
        for (long id : presenceAudienceFor(userId)) {
            boolean online = broker.isOnline(id) && !authClient.isFrozen(id);
            b.addPresenceSnapshot(PresenceEntry.newBuilder().setUserId(id).setOnline(online));
        }
        return b.build();
    }

    /**
     * Add a new member to an existing MESSAGING_GROUP conversation.
     *
     * Rules:
     *   - Conversation must be of type MESSAGING_GROUP.
     *   - Requester must be an active (non-deleted) member.
     *   - If the new user is already a participant the call is a no-op.
     *
     * Returns the conversation so the caller can broadcast it back to the
     * new member (they need the conversationId to subscribe to the WS topic).
     */
    @Transactional
    public Conversation addMessagingGroupMember(long conversationId, long requesterId, long newUserId) {
        Conversation c = conversations.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> ServiceException.notFound("Conversation not found"));
        if (c.getType() != Conversation.Type.MESSAGING_GROUP)
            throw ServiceException.invalid("Not a messaging group");
        assertActiveMember(conversationId, requesterId);

        if (requesterId == newUserId)
            throw ServiceException.invalid("Cannot add yourself");
        ConversationParticipant requester = participants.findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, requesterId)
                .orElseThrow(() -> ServiceException.forbidden("Not a participant"));
        if (!canAddMembers(c, requester))
            throw ServiceException.forbidden("No permission to add members");
        if (!userClient.friendIds(requesterId).contains(newUserId))
            throw ServiceException.forbidden("Only friends can be added to messaging groups");
        if (userClient.isBlockedEitherWay(requesterId, newUserId))
            throw ServiceException.forbidden("Blocked");

        Optional<ConversationParticipant> existing = participants.findByConversationIdAndUserId(conversationId, newUserId);
        if (existing.isPresent()) {
            ConversationParticipant p = existing.get();
            if (p.getDeletedAt() == null) return c;
            ensureMessagingGroupHasRoom(conversationId);
            p.setDeletedAt(null);
            p.setMuted(false);
            p.setJoinedAt(LocalDateTime.now(ZoneOffset.UTC));
            p.setLastReadAt(LocalDateTime.now(ZoneOffset.UTC));
            participants.save(p);
        } else {
            ensureMessagingGroupHasRoom(conversationId);
            participants.save(ConversationParticipant.builder()
                    .conversationId(conversationId)
                    .userId(newUserId)
                    .joinedAt(LocalDateTime.now(ZoneOffset.UTC))
                    .lastReadAt(LocalDateTime.now(ZoneOffset.UTC))
                    .role("MEMBER")
                    .build());
        }

        saveSystemMessage(c, userClient.nickname(requesterId) + " added " + userClient.nickname(newUserId) + " to the group");
        notifyMessagingGroupEventAfterCommit("MESSAGING_GROUP_ADDED", conversationId, activeUserIds(conversationId));
        return c;
    }

    public void broadcastPresence(long userId, boolean online) {
        if (authClient.isFrozen(userId)) {
            online = false;
        }
        ChatEvent evt = ChatEvent.newBuilder()
                .setType("PRESENCE_UPDATE")
                .setSubjectUserId(userId)
                .setOnline(online)
                .build();
        for (long id : presenceAudienceFor(userId)) broker.sendTo(id, evt);
    }

    // --- helpers ------------------------------------------------------------

    private void assertActiveMember(long conversationId, long userId) {
        activeParticipant(conversationId, userId);
    }

    /** Users whose online status this viewer cares about: friends + messaging-group co-members. */
    private Set<Long> presenceAudienceFor(long userId) {
        Set<Long> audience = new LinkedHashSet<>(userClient.friendIds(userId));
        for (ConversationParticipant mine : participants.findByUserIdAndDeletedAtIsNull(userId)) {
            Conversation c = conversations.findByIdAndDeletedAtIsNull(mine.getConversationId()).orElse(null);
            if (c == null || c.getType() != Conversation.Type.MESSAGING_GROUP) continue;
            for (ConversationParticipant member : participants.findByConversationIdAndDeletedAtIsNull(c.getId())) {
                if (!member.getUserId().equals(userId)) audience.add(member.getUserId());
            }
        }
        return audience;
    }

    public boolean isBlockedByMe(long viewerId, long participantUserId) {
        if (viewerId == participantUserId) return false;
        return userClient.blockedByMe(viewerId, participantUserId);
    }

    public boolean isBlocksMe(long viewerId, long participantUserId) {
        if (viewerId == participantUserId) return false;
        return userClient.blocksMe(viewerId, participantUserId);
    }

    private boolean isMessagingGroup(Conversation c) {
        return c.getType() == Conversation.Type.MESSAGING_GROUP;
    }

    /** Hide only messages from users the viewer blocked — not the reverse. */
    private boolean shouldHideGroupMessageFrom(long viewerId, long senderId) {
        if (senderId <= 0 || viewerId == senderId) return false;
        return userClient.blockedByMe(viewerId, senderId);
    }

    private long countVisibleUnread(Conversation c, long readerId, LocalDateTime lastRead) {
        if (!isMessagingGroup(c)) {
            return messages.countUnreadFor(c.getId(), readerId, lastRead);
        }
        return countVisibleUnread(c.getId(), readerId, lastRead, userClient.blockedByMeIds(readerId));
    }

    private long countVisibleUnread(long conversationId, long readerId, LocalDateTime lastRead, Set<Long> hidden) {
        if (hidden.isEmpty()) {
            return messages.countUnreadFor(conversationId, readerId, lastRead);
        }
        return messages.findUnreadMessages(conversationId, readerId, lastRead).stream()
                .filter(m -> !hidden.contains(m.getSenderId()))
                .count();
    }

    private int visibleUnreadForParticipant(ConversationParticipant p, long userId, Set<Long> hidden) {
        Conversation c = conversations.findByIdAndDeletedAtIsNull(p.getConversationId()).orElse(null);
        if (c == null || !isMessagingGroup(c)) {
            return (int) messages.countUnreadFor(p.getConversationId(), userId, p.getLastReadAt());
        }
        return (int) countVisibleUnread(p.getConversationId(), userId, p.getLastReadAt(), hidden);
    }

    private Page<com.serdar.proto.chat.ChatMessage> getVisibleMessagePage(
            long conversationId, long callerId, int page, int size, Pageable pageable) {
        Set<Long> hidden = userClient.blockedByMeIds(callerId);
        int toSkip = page * size;
        List<com.serdar.proto.chat.ChatMessage> batch = new ArrayList<>();
        int dbPage = 0;
        int visibleSkipped = 0;
        int chunk = Math.max(size, 50);
        while (batch.size() < size) {
            Page<Message> raw = messages.findByConversationIdOrderByCreatedAtDesc(
                    conversationId, PageRequest.of(dbPage++, chunk));
            if (raw.isEmpty()) break;
            for (Message m : raw.getContent()) {
                if (hidden.contains(m.getSenderId())) continue;
                if (visibleSkipped++ < toSkip) continue;
                batch.add(decrypt(m));
                if (batch.size() >= size) break;
            }
            if (raw.isLast()) break;
        }
        long totalVisible = countVisibleMessages(conversationId, hidden);
        return new org.springframework.data.domain.PageImpl<>(batch, pageable, totalVisible);
    }

    private long countVisibleMessages(long conversationId, Set<Long> hidden) {
        if (hidden.isEmpty()) {
            return messages.countByConversationId(conversationId);
        }
        long total = 0;
        int dbPage = 0;
        int chunk = 200;
        while (true) {
            Page<Message> raw = messages.findByConversationIdOrderByCreatedAtDesc(
                    conversationId, PageRequest.of(dbPage++, chunk));
            for (Message m : raw.getContent()) {
                if (!hidden.contains(m.getSenderId())) total++;
            }
            if (raw.isLast()) break;
        }
        return total;
    }

    private void ensureMessagingGroupHasRoom(long conversationId) {
        long activeMembers = participants.countByConversationIdAndDeletedAtIsNull(conversationId);
        if (activeMembers >= limits.maxMessagingGroupMembers()) {
            throw ServiceException.invalid("Messaging group member limit exceeded");
        }
    }

    private ConversationParticipant activeParticipant(long conversationId, long userId) {
        return participants.findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(() -> ServiceException.forbidden("Not a participant"));
    }

    private Conversation requireMessagingGroup(long conversationId) {
        Conversation c = conversations.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> ServiceException.notFound("Conversation not found"));
        if (c.getType() != Conversation.Type.MESSAGING_GROUP)
            throw ServiceException.invalid("Not a messaging group");
        return c;
    }

    private Set<Long> activeUserIds(long conversationId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ConversationParticipant p : participants.findByConversationIdAndDeletedAtIsNull(conversationId)) {
            ids.add(p.getUserId());
        }
        return ids;
    }

    private boolean isOwner(Conversation c, ConversationParticipant p) {
        return Objects.equals(c.getCreatedById(), p.getUserId()) || "ADMIN".equalsIgnoreCase(p.getRole());
    }

    private boolean hasPermission(Conversation c, ConversationParticipant p, Permission permission) {
        if (isOwner(c, p)) return true;
        return switch (permission) {
            case CHANGE_PHOTO -> Boolean.TRUE.equals(p.getCanChangePhoto());
            case CHANGE_DESCRIPTION -> Boolean.TRUE.equals(p.getCanChangeDescription());
            case CHANGE_NAME -> Boolean.TRUE.equals(p.getCanChangeName());
            case REMOVE_MEMBERS -> Boolean.TRUE.equals(p.getCanRemoveMembers());
            case ADD_MEMBERS -> Boolean.TRUE.equals(p.getCanAddMembers());
        };
    }

    private boolean canAddMembers(Conversation c, ConversationParticipant p) {
        return hasPermission(c, p, Permission.ADD_MEMBERS);
    }

    private void requirePermission(Conversation c, ConversationParticipant p, Permission permission) {
        if (!hasPermission(c, p, permission)) {
            throw ServiceException.forbidden("Missing permission");
        }
    }

    private void applyPermission(
            Conversation c,
            ConversationParticipant requester,
            ConversationParticipant target,
            Permission permission,
            boolean value
    ) {
        boolean current = getPermission(target, permission);
        if (current == value) {
            return;
        }
        if (isOwner(c, target)) {
            throw ServiceException.forbidden("Cannot change admin permissions");
        }
        if (isOwner(c, requester)) {
            setPermission(target, permission, value);
            return;
        }
        if (!hasPermission(c, requester, permission)) {
            throw ServiceException.forbidden("Cannot assign permission you do not have");
        }
        if (!value) {
            throw ServiceException.forbidden("Only group admins can revoke permissions");
        }
        setPermission(target, permission, value);
    }

    private boolean getPermission(ConversationParticipant p, Permission permission) {
        return switch (permission) {
            case CHANGE_PHOTO -> Boolean.TRUE.equals(p.getCanChangePhoto());
            case CHANGE_DESCRIPTION -> Boolean.TRUE.equals(p.getCanChangeDescription());
            case CHANGE_NAME -> Boolean.TRUE.equals(p.getCanChangeName());
            case REMOVE_MEMBERS -> Boolean.TRUE.equals(p.getCanRemoveMembers());
            case ADD_MEMBERS -> Boolean.TRUE.equals(p.getCanAddMembers());
        };
    }

    private void setPermission(ConversationParticipant p, Permission permission, boolean value) {
        switch (permission) {
            case CHANGE_PHOTO -> p.setCanChangePhoto(value);
            case CHANGE_DESCRIPTION -> p.setCanChangeDescription(value);
            case CHANGE_NAME -> p.setCanChangeName(value);
            case REMOVE_MEMBERS -> p.setCanRemoveMembers(value);
            case ADD_MEMBERS -> p.setCanAddMembers(value);
        }
    }

    private ConversationParticipant randomParticipant(List<ConversationParticipant> activeParticipants) {
        if (activeParticipants.isEmpty()) throw ServiceException.invalid("No members left");
        return activeParticipants.get(new Random().nextInt(activeParticipants.size()));
    }

    private void grantAdmin(ConversationParticipant participant) {
        participant.setRole("ADMIN");
        participant.setCanChangePhoto(true);
        participant.setCanChangeDescription(true);
        participant.setCanChangeName(true);
        participant.setCanRemoveMembers(true);
        participant.setCanAddMembers(true);
    }

    private Message saveSystemMessage(Conversation c, String plaintext) {
        AesGcm.Enc enc = aes.encrypt(plaintext, AesGcm.aad(c.getId(), 0));
        Message m = messages.save(Message.builder()
                .id(MessageIdGenerator.nextId())
                .conversationId(c.getId())
                .senderId(0L)
                .contentCipherB64(enc.cipherB64())
                .contentIvB64(enc.ivB64())
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());
        broadcastMessage(c, m, plaintext);
        return m;
    }

    private static final Pattern UPLOAD_OBJECT_KEY =
            Pattern.compile("^/uploads/[a-zA-Z0-9][a-zA-Z0-9._-]*$");

    private static String validateGroupImageUrl(String imageUrl) {
        String trimmed = blankToNull(imageUrl);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.contains("..")) {
            throw ServiceException.invalid("Invalid image URL");
        }
        String path = uploadPath(trimmed);
        if (!UPLOAD_OBJECT_KEY.matcher(path).matches()) {
            throw ServiceException.invalid("Image URL must point to an uploaded file under /uploads/");
        }
        return trimmed;
    }

    private static String uploadPath(String url) {
        if (url.startsWith("/uploads/")) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            try {
                return URI.create(url).getPath();
            } catch (IllegalArgumentException e) {
                throw ServiceException.invalid("Invalid image URL");
            }
        }
        throw ServiceException.invalid("Image URL must point to an uploaded file under /uploads/");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    private void notifyMessagingGroupEventAfterCommit(String type, long conversationId, Set<Long> userIds) {
        Runnable send = () -> {
            ChatEvent event = ChatEvent.newBuilder()
                    .setType(type)
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

    private void notifyUnreadAfterCommit(long userId, long conversationId, int unread, int totalUnread) {
        Runnable send = () -> broker.sendTo(userId,
                ChatEvent.newBuilder()
                        .setType("UNREAD_COUNT_UPDATE")
                        .setConversationId(conversationId)
                        .setUnreadCount(unread)
                        .setTotalUnreadCount(totalUnread)
                        .build());

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

    private enum Permission { CHANGE_PHOTO, CHANGE_DESCRIPTION, CHANGE_NAME, REMOVE_MEMBERS, ADD_MEMBERS }

    public record MarkReadResult(int unread, int totalUnread, LocalDateTime lastReadAt) {}
    public record UnreadCounts(int total, Map<Long, Integer> perConversation) {}
    public record ReadState(LocalDateTime myLastReadAt, LocalDateTime friendLastReadAt,
                            Long seenMyMessageId, long friendUserId, long myUserId) {}
    public record PermissionValues(
            boolean canChangePhoto,
            boolean canChangeDescription,
            boolean canChangeName,
            boolean canRemoveMembers,
            boolean canAddMembers
    ) {}
    public record MessagingGroupDetail(
            Conversation conversation,
            List<ConversationParticipant> participants,
            ConversationParticipant me,
            List<ConversationParticipant> knownParticipants
    ) {}
}

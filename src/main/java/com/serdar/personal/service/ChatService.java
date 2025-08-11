package com.serdar.personal.service;

import com.serdar.personal.model.Conversation;
import com.serdar.personal.model.ConversationParticipant;
import com.serdar.personal.model.Message;
import com.serdar.personal.model.User;
import com.serdar.personal.model.dto.MessageDTO;
import com.serdar.personal.repository.ConversationParticipantRepository;
import com.serdar.personal.repository.ConversationRepository;
import com.serdar.personal.repository.MessageRepository;
import com.serdar.personal.repository.UserRepository;
import com.serdar.personal.security.AesGcmService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final MessageRepository msgRepo;
    private final ConversationRepository convRepo;
    private final UserRepository userRepo;
    private final ConversationParticipantRepository participantRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final AesGcmService aes;
    private final FriendService friendService;

    /** userId -> açık WS session sayısı (çoklu tab desteği) */
    private final Map<Long, AtomicInteger> sessions = new ConcurrentHashMap<>();

    private boolean isOnline(Long userId) {
        AtomicInteger c = sessions.get(userId);
        return c != null && c.get() > 0;
    }

    /** Arkadaşları friendship tablosundan getir (sohbet açmadan da çalışır). */
    private List<User> getFriendsOfUser(Long userId) {
        return friendService.getFriendEntitiesOf(userId);
    }

    /** Dışarıdan da çağrılabilsin diye public yaptım (WS snapshot tetiklemek için). */
    public void sendPresenceSnapshotTo(User me) {
        List<User> friends = getFriendsOfUser(me.getId());
        List<Map<String, Object>> payloadUsers = new ArrayList<>();
        for (User f : friends) {
            payloadUsers.add(Map.of(
                    "userId", f.getId(),
                    "online", isOnline(f.getId())
            ));
        }
        Map<String, Object> ev = Map.of(
                "type", "PRESENCE_SNAPSHOT",
                "users", payloadUsers
        );
        messagingTemplate.convertAndSendToUser(me.getEmail(), "/queue/friends", ev);
    }

    private void broadcastPresenceUpdate(User me, boolean online) {
        List<User> friends = getFriendsOfUser(me.getId());
        Map<String, Object> ev = Map.of(
                "type", "PRESENCE_UPDATE",
                "userId", me.getId(),
                "online", online
        );
        for (User f : friends) {
            messagingTemplate.convertAndSendToUser(f.getEmail(), "/queue/friends", ev);
        }
    }

    @EventListener
    public void onWsConnected(SessionConnectedEvent ev) {
        try {
            if (ev.getUser() == null) return;
            userRepo.findByEmail(ev.getUser().getName()).ifPresent(me -> {
                int after = sessions.computeIfAbsent(me.getId(), k -> new AtomicInteger()).incrementAndGet();
                log.info("WS connect: user={} sessions={}", me.getId(), after);
                if (after == 1) {
                    // offline -> online
                    broadcastPresenceUpdate(me, true);
                }
                // bağlanan kullanıcıya snapshot (isteğe bağlı, client ayrıca tetikliyor)
                sendPresenceSnapshotTo(me);
            });
        } catch (Exception e) {
            log.warn("onWsConnected error", e);
        }
    }

    @EventListener
    public void onWsDisconnected(SessionDisconnectEvent ev) {
        try {
            if (ev.getUser() == null) return;
            userRepo.findByEmail(ev.getUser().getName()).ifPresent(me -> {
                AtomicInteger ai = sessions.get(me.getId());
                if (ai == null) return;
                int after = ai.decrementAndGet();
                log.info("WS disconnect: user={} sessions={}", me.getId(), after);
                if (after <= 0) {
                    sessions.remove(me.getId());
                    // online -> offline
                    broadcastPresenceUpdate(me, false);
                }
            });
        } catch (Exception e) {
            log.warn("onWsDisconnected error", e);
        }
    }

    // ====== SEND ======

    @Transactional
    public Message send(Long conversationId, Long senderId, String plaintext) {
        try {
            log.info("ChatService.send() - conv={}, sender={}, content='{}'",
                    conversationId, senderId, plaintext);

            if (plaintext == null || plaintext.isBlank())
                throw new IllegalArgumentException("Empty message");
            if (plaintext.length() > 2000)
                throw new IllegalArgumentException("Message too long");

            // Güvenlik: gönderen o konuşmanın katılımcısı mı?
            var senderPart = participantRepo.findByConversationIdAndUserId(conversationId, senderId);
            if (senderPart.isEmpty()) {
                throw new IllegalArgumentException("Sender not in conversation");
            }

            Conversation conv = convRepo.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
            User sender = userRepo.findById(senderId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + senderId));

            var enc = aes.encrypt(plaintext, AesGcmService.aad(conversationId, senderId));

            Message m = Message.builder()
                    .conversation(conv)
                    .sender(sender)
                    .contentCipherB64(enc.getCipherB64())
                    .contentIvB64(enc.getIvB64())
                    .createdAt(LocalDateTime.now())
                    .build();

            Message saved = msgRepo.save(m);
            log.info("Message saved id={}", saved.getId());

            return saved;
        } catch (Exception e) {
            log.error("ChatService.send() error", e);
            throw e;
        }
    }

    /** WS payload’ı kaydet + user-queue’ya yayın + unread event */
    @Transactional
    public void processWsSendPayload(Map<String, Object> payload) {
        Long conversationId = Long.valueOf(payload.get("conversationId").toString());
        Long senderId       = Long.valueOf(payload.get("senderId").toString());
        String content      = String.valueOf(payload.get("content"));

        Message saved = send(conversationId, senderId, content);

        MessageDTO dto = MessageDTO.builder()
                .id(saved.getId())
                .senderId(saved.getSender().getId())
                .conversationId(saved.getConversation().getId())
                .content(content)
                .createdAt(saved.getCreatedAt())
                .build();

        // YALNIZCA user-queue (topic kaldırıldı)
        List<ConversationParticipant> parts =
                participantRepo.findByConversationIdAndDeletedAtIsNull(conversationId);
        for (ConversationParticipant p : parts) {
            messagingTemplate.convertAndSendToUser(
                    p.getUser().getEmail(), "/queue/messages/" + conversationId, dto);
        }

        // unread (gönderen hariç)
        for (ConversationParticipant p : parts) {
            if (p.getUser().getId().equals(senderId)) continue;
            int unreadCount = getUnreadCount(p.getUser(), conversationId);
            Map<String, Object> ev = new HashMap<>();
            ev.put("type", "UNREAD_COUNT_UPDATE");
            ev.put("conversationId", conversationId);
            ev.put("unreadCount", unreadCount);
            ev.put("totalUnreadCount", getTotalUnreadCount(p.getUser()));
            messagingTemplate.convertAndSendToUser(p.getUser().getEmail(), "/queue/friends", ev);
        }
    }

    // ====== HISTORY ======

    /** Şifre çözülmüş içerikle DTO dönen sayfalama (DESC by createdAt) */
    public Page<MessageDTO> getPage(Long convId, int page, int size) {
        Page<Message> p = msgRepo.findByConversationIdOrderByCreatedAtDesc(
                convId, PageRequest.of(page, size));
        return p.map(this::decryptToDTO);
    }

    /** İlk açılış için: son 'limit' mesaj (ASC) */
    public List<MessageDTO> getLatestAscending(Long convId, int limit) {
        Page<Message> p = msgRepo.findByConversationIdOrderByCreatedAtDesc(
                convId, PageRequest.of(0, limit));

        List<MessageDTO> descDtos = p.getContent().stream()
                .map(this::decryptToDTO)
                .collect(Collectors.toList());

        Collections.reverse(descDtos); // DESC → ASC
        return descDtos;
    }

    private MessageDTO decryptToDTO(Message m) {
        try {
            String decrypted = aes.decrypt(
                    m.getContentIvB64(),
                    m.getContentCipherB64(),
                    AesGcmService.aad(m.getConversation().getId(), m.getSender().getId())
            );
            return new MessageDTO(
                    m.getId(), m.getSender().getId(), m.getConversation().getId(), decrypted, m.getCreatedAt()
            );
        } catch (Exception e) {
            log.error("Decrypt failed msgId={}", m.getId(), e);
            return new MessageDTO(
                    m.getId(), m.getSender().getId(), m.getConversation().getId(), "[Decryption failed]", m.getCreatedAt()
            );
        }
    }

    // ====== READ / SEEN ======

    /** Sohbet penceresi açıldığında çağır: last_read_at now + READ event publish */
    @Transactional
    public Map<String, Object> markReadAndBroadcast(Long convId, Principal principal) {
        User me = userRepo.findByEmail(principal.getName()).orElseThrow();

        var mePart = participantRepo.findByConversationIdAndUserId(convId, me.getId())
                .orElseThrow();

        mePart.setLastReadAt(LocalDateTime.now());
        participantRepo.save(mePart);

        LocalDateTime myLastReadAt = mePart.getLastReadAt();

        Map<String, Object> ev = new HashMap<>();
        ev.put("type", "READ");
        ev.put("conversationId", convId);
        ev.put("readerUserId", me.getId());
        ev.put("lastReadAt", myLastReadAt != null ? myLastReadAt.toString() : null);

        // YALNIZCA user-queue (topic kaldırıldı)
        participantRepo.findByConversationIdAndDeletedAtIsNull(convId)
                .forEach(p -> messagingTemplate.convertAndSendToUser(
                        p.getUser().getEmail(), "/queue/messages/" + convId, ev));

        // unread toplamlarını da güncelle
        participantRepo.findByConversationIdAndDeletedAtIsNull(convId).forEach(p -> {
            int unread = getUnreadCount(p.getUser(), convId);
            Map<String, Object> unreadEv = new HashMap<>();
            unreadEv.put("type", "UNREAD_COUNT_UPDATE");
            unreadEv.put("conversationId", convId);
            unreadEv.put("unreadCount", unread);
            unreadEv.put("totalUnreadCount", getTotalUnreadCount(p.getUser()));
            messagingTemplate.convertAndSendToUser(p.getUser().getEmail(), "/queue/friends", unreadEv);
        });

        return ev;
    }

    /** Karşı tarafın last_read_at + “görülen son benim mesajımın id”si */
    public Map<String, Object> getReadState(Long convId, Principal principal) {
        User me = userRepo.findByEmail(principal.getName()).orElseThrow();
        List<ConversationParticipant> parts =
                participantRepo.findByConversationIdAndDeletedAtIsNull(convId);

        if (parts.size() < 2) {
            return Map.of("myLastReadAt", null, "friendLastReadAt", null, "seenMyMessageId", null);
        }

        ConversationParticipant myP = parts.stream()
                .filter(p -> p.getUser().getId().equals(me.getId()))
                .findFirst().orElseThrow();

        ConversationParticipant friendP = parts.stream()
                .filter(p -> !p.getUser().getId().equals(me.getId()))
                .findFirst().orElseThrow();

        LocalDateTime myLastReadAt = myP.getLastReadAt();
        LocalDateTime friendLastReadAt = friendP.getLastReadAt();

        Long seenMyMessageId = null;
        if (friendLastReadAt != null) {
            Optional<Message> lastSeenMine =
                    msgRepo.findTopByConversationIdAndSenderIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                            convId, me.getId(), friendLastReadAt);
            seenMyMessageId = lastSeenMine.map(Message::getId).orElse(null);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("myLastReadAt", myLastReadAt != null ? myLastReadAt.toString() : null);
        resp.put("friendLastReadAt", friendLastReadAt != null ? friendLastReadAt.toString() : null);
        resp.put("seenMyMessageId", seenMyMessageId);
        resp.put("friendUserId", friendP.getUser().getId());
        resp.put("myUserId", me.getId());
        return resp;
    }

    // ====== UNREAD TOTALS (ChatManager uyumu) ======

    public Map<String, Object> getUnreadCounts(Principal principal) {
        User me = userRepo.findByEmail(principal.getName()).orElseThrow();
        List<ConversationParticipant> myParts =
                participantRepo.findByUserIdAndDeletedAtIsNull(me.getId());

        Map<Long, Integer> conversationCounts = new HashMap<>();
        for (ConversationParticipant p : myParts) {
            Long cid = p.getConversation().getId();
            conversationCounts.put(cid, getUnreadCount(me, cid));
        }

        int total = conversationCounts.values().stream().mapToInt(Integer::intValue).sum();
        return Map.of("totalCount", total, "conversationCounts", conversationCounts);
    }

    @Transactional
    public Map<String, Object> markReadByManager(Long conversationId, Principal principal) {
        User me = userRepo.findByEmail(principal.getName()).orElseThrow();
        var part = participantRepo.findByConversationIdAndUserId(conversationId, me.getId())
                .orElseThrow();
        part.setLastReadAt(LocalDateTime.now());
        participantRepo.save(part);

        int convUnread = getUnreadCount(me, conversationId);
        int total = getTotalUnreadCount(me);
        return Map.of("conversationId", conversationId, "unreadCount", convUnread, "totalUnreadCount", total);
    }

    // ====== helpers ======
    private int getUnreadCount(User user, Long conversationId) {
        var partOpt = participantRepo.findByConversationIdAndUserId(conversationId, user.getId());
        if (partOpt.isEmpty()) return 0;

        LocalDateTime lastReadAt = partOpt.get().getLastReadAt();
        if (lastReadAt == null) {
            return msgRepo.countByConversationId(conversationId);
        }
        return msgRepo.countByConversationIdAndCreatedAtAfter(conversationId, lastReadAt);
    }

    private int getTotalUnreadCount(User user) {
        List<ConversationParticipant> parts =
                participantRepo.findByUserIdAndDeletedAtIsNull(user.getId());
        return parts.stream()
                .mapToInt(p -> getUnreadCount(user, p.getConversation().getId()))
                .sum();
    }
}

package com.serdar.chat.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.serdar.chat.model.Message;
import com.serdar.chat.repository.MessageStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CassandraMessageStore implements MessageStore {

    /** Cap rows fetched for offset pagination — prevents unbounded memory use. */
    private static final int MAX_PAGE_FETCH = 5_000;

    private final CqlSession session;
    private final CassandraMessageRepository messages;
    private final CassandraMessageBySenderRepository bySender;

    @Value("${spring.cassandra.keyspace-name}")
    private String keyspace;

    private volatile boolean initialized;
    private PreparedStatement selectConversationAll;
    private PreparedStatement selectConversationLimited;
    private PreparedStatement selectConversationSince;
    private PreparedStatement selectOne;
    private PreparedStatement countConversation;
    private PreparedStatement deleteConversation;
    private PreparedStatement deleteBySender;
    private PreparedStatement updateConversation;
    private PreparedStatement updateBySender;
    private PreparedStatement softDeleteConversation;

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            session.execute(
                    "CREATE KEYSPACE IF NOT EXISTS " + keyspace
                            + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
            session.execute("USE " + keyspace);
            session.execute("""
                    CREATE TABLE IF NOT EXISTS messages_by_conversation (
                        conversation_id bigint,
                        created_at timestamp,
                        message_id bigint,
                        sender_id bigint,
                        content_cipher_b64 text,
                        content_iv_b64 text,
                        edited_at timestamp,
                        deleted boolean,
                        PRIMARY KEY (conversation_id, created_at, message_id)
                    ) WITH CLUSTERING ORDER BY (created_at DESC, message_id DESC)
                    """);
            session.execute("""
                    CREATE TABLE IF NOT EXISTS messages_by_sender (
                        conversation_id bigint,
                        sender_id bigint,
                        created_at timestamp,
                        message_id bigint,
                        content_cipher_b64 text,
                        content_iv_b64 text,
                        PRIMARY KEY ((conversation_id, sender_id), created_at, message_id)
                    ) WITH CLUSTERING ORDER BY (created_at DESC, message_id DESC)
                    """);
            try {
                session.execute("ALTER TABLE messages_by_conversation ADD edited_at timestamp");
            } catch (Exception ignored) {
                // column already exists
            }
            try {
                session.execute("ALTER TABLE messages_by_conversation ADD deleted boolean");
            } catch (Exception ignored) {
                // column already exists
            }
            String cols = "conversation_id, created_at, message_id, sender_id, content_cipher_b64, content_iv_b64, edited_at, deleted";
            selectConversationAll = session.prepare(
                    "SELECT " + cols + " FROM messages_by_conversation WHERE conversation_id = ?");
            selectConversationLimited = session.prepare(
                    "SELECT " + cols + " FROM messages_by_conversation WHERE conversation_id = ? LIMIT ?");
            selectConversationSince = session.prepare(
                    "SELECT " + cols + " FROM messages_by_conversation WHERE conversation_id = ? AND created_at > ?");
            selectOne = session.prepare(
                    "SELECT " + cols + " FROM messages_by_conversation"
                            + " WHERE conversation_id = ? AND created_at = ? AND message_id = ?");
            countConversation = session.prepare(
                    "SELECT COUNT(*) FROM messages_by_conversation WHERE conversation_id = ?");
            deleteConversation = session.prepare(
                    "DELETE FROM messages_by_conversation WHERE conversation_id = ? AND created_at = ? AND message_id = ?");
            deleteBySender = session.prepare(
                    "DELETE FROM messages_by_sender WHERE conversation_id = ? AND sender_id = ? AND created_at = ? AND message_id = ?");
            updateConversation = session.prepare(
                    "UPDATE messages_by_conversation SET content_cipher_b64 = ?, content_iv_b64 = ?, edited_at = ?"
                            + " WHERE conversation_id = ? AND created_at = ? AND message_id = ?");
            updateBySender = session.prepare(
                    "UPDATE messages_by_sender SET content_cipher_b64 = ?, content_iv_b64 = ?"
                            + " WHERE conversation_id = ? AND sender_id = ? AND created_at = ? AND message_id = ?");
            softDeleteConversation = session.prepare(
                    "UPDATE messages_by_conversation SET deleted = true, content_cipher_b64 = null, content_iv_b64 = null"
                            + " WHERE conversation_id = ? AND created_at = ? AND message_id = ?");
            initialized = true;
        }
    }

    @Override
    public Message save(Message m) {
        ensureInitialized();
        Instant created = m.getCreatedAt().toInstant(ZoneOffset.UTC);
        CassandraMessage row = new CassandraMessage();
        CassandraMessage.Key key = new CassandraMessage.Key();
        key.setConversationId(m.getConversationId());
        key.setCreatedAt(created);
        key.setMessageId(m.getId());
        row.setKey(key);
        row.setSenderId(m.getSenderId());
        row.setContentCipherB64(m.getContentCipherB64());
        row.setContentIvB64(m.getContentIvB64());
        messages.save(row);

        if (m.getSenderId() != null && m.getSenderId() > 0) {
            CassandraMessageBySender senderRow = new CassandraMessageBySender();
            CassandraMessageBySender.Key sKey = new CassandraMessageBySender.Key();
            sKey.setConversationId(m.getConversationId());
            sKey.setSenderId(m.getSenderId());
            sKey.setCreatedAt(created);
            sKey.setMessageId(m.getId());
            senderRow.setKey(sKey);
            senderRow.setContentCipherB64(m.getContentCipherB64());
            senderRow.setContentIvB64(m.getContentIvB64());
            bySender.save(senderRow);
        }
        return m;
    }

    @Override
    public Optional<Message> findMessage(long conversationId, long messageId, long createdAtMillis) {
        ensureInitialized();
        Optional<Message> byId = loadAll(conversationId).stream()
                .filter(m -> m.getId() != null && m.getId() == messageId)
                .findFirst();
        if (byId.isPresent()) {
            return byId;
        }
        if (createdAtMillis > 0) {
            Instant created = Instant.ofEpochMilli(createdAtMillis);
            Row row = session.execute(selectOne.bind(conversationId, created, messageId)).one();
            if (row != null) {
                return Optional.of(fromRow(row));
            }
        }
        return Optional.empty();
    }

    @Override
    public void deleteMessage(long conversationId, long messageId, long createdAtMillis) {
        ensureInitialized();
        Message m = findMessage(conversationId, messageId, createdAtMillis)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        Instant created = m.getCreatedAt().toInstant(ZoneOffset.UTC);
        session.execute(softDeleteConversation.bind(conversationId, created, messageId));
        if (m.getSenderId() != null && m.getSenderId() > 0) {
            session.execute(deleteBySender.bind(conversationId, m.getSenderId(), created, messageId));
        }
    }

    @Override
    public void editMessage(long conversationId, long messageId, long createdAtMillis,
                            String contentCipherB64, String contentIvB64, LocalDateTime editedAt) {
        ensureInitialized();
        Message m = findMessage(conversationId, messageId, createdAtMillis)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        Instant created = m.getCreatedAt().toInstant(ZoneOffset.UTC);
        Instant edited = editedAt.toInstant(ZoneOffset.UTC);
        session.execute(updateConversation.bind(
                contentCipherB64, contentIvB64, edited, conversationId, created, messageId));
        if (m.getSenderId() != null && m.getSenderId() > 0) {
            session.execute(updateBySender.bind(
                    contentCipherB64, contentIvB64, conversationId, m.getSenderId(), created, messageId));
        }
    }

    @Override
    public void deleteByConversationId(long conversationId) {
        ensureInitialized();
        for (Message m : loadAll(conversationId)) {
            Instant created = m.getCreatedAt().toInstant(ZoneOffset.UTC);
            session.execute(deleteConversation.bind(conversationId, created, m.getId()));
            if (m.getSenderId() != null && m.getSenderId() > 0) {
                session.execute(deleteBySender.bind(conversationId, m.getSenderId(), created, m.getId()));
            }
        }
    }

    @Override
    public Page<Message> findByConversationIdOrderByCreatedAtDesc(long conversationId, Pageable pageable) {
        ensureInitialized();
        long total = countByConversationId(conversationId);
        int offset = (int) pageable.getOffset();
        int size = pageable.getPageSize();
        if (offset >= total || size <= 0) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        if (offset >= MAX_PAGE_FETCH) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        int fetchLimit = Math.min(offset + size, MAX_PAGE_FETCH);
        List<Message> fetched = loadLimited(conversationId, fetchLimit);
        int from = Math.min(offset, fetched.size());
        int to = Math.min(from + size, fetched.size());
        if (from >= to) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        return new PageImpl<>(fetched.subList(from, to), pageable, total);
    }

    @Override
    public long countByConversationId(long conversationId) {
        ensureInitialized();
        Row row = session.execute(countConversation.bind(conversationId)).one();
        return row == null ? 0L : row.getLong(0);
    }

    @Override
    public long countUnreadFor(long conversationId, long meId, LocalDateTime lastRead) {
        ensureInitialized();
        Instant cutoff = lastRead == null ? Instant.EPOCH : lastRead.toInstant(ZoneOffset.UTC);
        return loadSince(conversationId, cutoff).stream()
                .filter(m -> countsAsUnread(m, meId))
                .count();
    }

    @Override
    public List<Message> findUnreadMessages(long conversationId, long meId, LocalDateTime lastRead) {
        ensureInitialized();
        Instant cutoff = lastRead == null ? Instant.EPOCH : lastRead.toInstant(ZoneOffset.UTC);
        return loadSince(conversationId, cutoff).stream()
                .filter(m -> countsAsUnread(m, meId))
                .toList();
    }

    @Override
    public List<Message> lastFromSenderBefore(long conversationId, long senderId, LocalDateTime cutoff, Pageable pageable) {
        ensureInitialized();
        Instant bound = cutoff.toInstant(ZoneOffset.UTC);
        List<CassandraMessageBySender> rows =
                bySender.findByKeyConversationIdAndKeySenderId(conversationId, senderId);
        return rows.stream()
                .map(this::fromSenderRow)
                .filter(m -> !m.getCreatedAt().toInstant(ZoneOffset.UTC).isAfter(bound))
                .sorted(Comparator.comparing(Message::getCreatedAt).reversed())
                .limit(pageable.getPageSize())
                .toList();
    }

    private static boolean countsAsUnread(Message m, long meId) {
        if (m.isDeleted()) {
            return false;
        }
        Long senderId = m.getSenderId();
        return senderId != null && senderId > 0 && senderId != meId;
    }

    private List<Message> loadAll(long conversationId) {
        List<Message> out = new ArrayList<>();
        for (Row row : session.execute(selectConversationAll.bind(conversationId))) {
            out.add(fromRow(row));
        }
        return out;
    }

    private List<Message> loadLimited(long conversationId, int limit) {
        List<Message> out = new ArrayList<>();
        for (Row row : session.execute(selectConversationLimited.bind(conversationId, limit))) {
            out.add(fromRow(row));
        }
        return out;
    }

    private List<Message> loadSince(long conversationId, Instant cutoff) {
        List<Message> out = new ArrayList<>();
        for (Row row : session.execute(selectConversationSince.bind(conversationId, cutoff))) {
            out.add(fromRow(row));
        }
        return out;
    }

    private Message fromRow(Row row) {
        Instant edited = row.getInstant("edited_at");
        Boolean deleted = row.getBoolean("deleted");
        return Message.builder()
                .id(row.getLong("message_id"))
                .conversationId(row.getLong("conversation_id"))
                .senderId(row.getLong("sender_id"))
                .contentCipherB64(row.getString("content_cipher_b64"))
                .contentIvB64(row.getString("content_iv_b64"))
                .createdAt(LocalDateTime.ofInstant(row.getInstant("created_at"), ZoneOffset.UTC))
                .editedAt(edited == null ? null : LocalDateTime.ofInstant(edited, ZoneOffset.UTC))
                .deleted(Boolean.TRUE.equals(deleted))
                .build();
    }

    private Message fromSenderRow(CassandraMessageBySender row) {
        return Message.builder()
                .id(row.getKey().getMessageId())
                .conversationId(row.getKey().getConversationId())
                .senderId(row.getKey().getSenderId())
                .contentCipherB64(row.getContentCipherB64())
                .contentIvB64(row.getContentIvB64())
                .createdAt(LocalDateTime.ofInstant(row.getKey().getCreatedAt(), ZoneOffset.UTC))
                .build();
    }
}


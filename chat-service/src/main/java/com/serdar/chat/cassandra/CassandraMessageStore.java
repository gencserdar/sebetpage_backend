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

@Repository
@RequiredArgsConstructor
public class CassandraMessageStore implements MessageStore {

    private final CqlSession session;
    private final CassandraMessageRepository messages;
    private final CassandraMessageBySenderRepository bySender;

    @Value("${spring.cassandra.keyspace-name}")
    private String keyspace;

    private volatile boolean initialized;
    private PreparedStatement selectConversation;
    private PreparedStatement deleteConversation;
    private PreparedStatement deleteBySender;

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
            selectConversation = session.prepare(
                    "SELECT conversation_id, created_at, message_id, sender_id, content_cipher_b64, content_iv_b64 "
                            + "FROM messages_by_conversation WHERE conversation_id = ?");
            deleteConversation = session.prepare(
                    "DELETE FROM messages_by_conversation WHERE conversation_id = ? AND created_at = ? AND message_id = ?");
            deleteBySender = session.prepare(
                    "DELETE FROM messages_by_sender WHERE conversation_id = ? AND sender_id = ? AND created_at = ? AND message_id = ?");
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
    public void deleteByConversationId(long conversationId) {
        ensureInitialized();
        List<Message> all = loadConversation(conversationId);
        for (Message m : all) {
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
        List<Message> all = loadConversation(conversationId);
        all.sort(Comparator.comparing(Message::getCreatedAt).reversed().thenComparing(Message::getId, Comparator.reverseOrder()));
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        if (start >= all.size()) {
            return new PageImpl<>(List.of(), pageable, all.size());
        }
        return new PageImpl<>(all.subList(start, end), pageable, all.size());
    }

    @Override
    public long countByConversationId(long conversationId) {
        ensureInitialized();
        return loadConversation(conversationId).size();
    }

    @Override
    public long countUnreadFor(long conversationId, long meId, LocalDateTime lastRead) {
        ensureInitialized();
        Instant cutoff = lastRead == null ? Instant.EPOCH : lastRead.toInstant(ZoneOffset.UTC);
        return loadConversation(conversationId).stream()
                .filter(m -> m.getSenderId() != meId)
                .filter(m -> m.getCreatedAt().toInstant(ZoneOffset.UTC).isAfter(cutoff))
                .count();
    }

    @Override
    public List<Message> findUnreadMessages(long conversationId, long meId, LocalDateTime lastRead) {
        ensureInitialized();
        Instant cutoff = lastRead == null ? Instant.EPOCH : lastRead.toInstant(ZoneOffset.UTC);
        return loadConversation(conversationId).stream()
                .filter(m -> m.getSenderId() != meId)
                .filter(m -> m.getCreatedAt().toInstant(ZoneOffset.UTC).isAfter(cutoff))
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

    private List<Message> loadConversation(long conversationId) {
        List<Message> out = new ArrayList<>();
        for (Row row : session.execute(selectConversation.bind(conversationId))) {
            out.add(fromRow(row));
        }
        return out;
    }

    private Message fromRow(Row row) {
        return Message.builder()
                .id(row.getLong("message_id"))
                .conversationId(row.getLong("conversation_id"))
                .senderId(row.getLong("sender_id"))
                .contentCipherB64(row.getString("content_cipher_b64"))
                .contentIvB64(row.getString("content_iv_b64"))
                .createdAt(LocalDateTime.ofInstant(row.getInstant("created_at"), ZoneOffset.UTC))
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

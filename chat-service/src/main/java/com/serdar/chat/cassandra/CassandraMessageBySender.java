package com.serdar.chat.cassandra;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;

import java.time.Instant;

@Table("messages_by_sender")
@Getter
@Setter
public class CassandraMessageBySender {
    @PrimaryKey
    private Key key;

    @Column("content_cipher_b64")
    private String contentCipherB64;
    @Column("content_iv_b64")
    private String contentIvB64;

    @PrimaryKeyClass
    @Getter
    @Setter
    public static class Key {
        @PrimaryKeyColumn(name = "conversation_id", type = PrimaryKeyType.PARTITIONED)
        private Long conversationId;

        @PrimaryKeyColumn(name = "sender_id", type = PrimaryKeyType.PARTITIONED)
        private Long senderId;

        @PrimaryKeyColumn(name = "created_at", type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
        private Instant createdAt;

        @PrimaryKeyColumn(name = "message_id", type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
        private Long messageId;
    }
}

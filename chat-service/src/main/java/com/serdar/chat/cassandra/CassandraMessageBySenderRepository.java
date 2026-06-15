package com.serdar.chat.cassandra;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CassandraMessageBySenderRepository
        extends CassandraRepository<CassandraMessageBySender, CassandraMessageBySender.Key> {

    List<CassandraMessageBySender> findByKeyConversationIdAndKeySenderId(
            Long conversationId, Long senderId);
}

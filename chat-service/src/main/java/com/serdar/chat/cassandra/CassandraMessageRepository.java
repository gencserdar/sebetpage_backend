package com.serdar.chat.cassandra;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CassandraMessageRepository extends CassandraRepository<CassandraMessage, CassandraMessage.Key> {}

package com.serdar.chat.service;

import java.util.concurrent.ThreadLocalRandom;

/** Time-ordered positive long IDs compatible with existing proto int64 fields. */
public final class MessageIdGenerator {
    private MessageIdGenerator() {}

    public static long nextId() {
        long ts = System.currentTimeMillis();
        int seq = ThreadLocalRandom.current().nextInt(1 << 20);
        return (ts << 20) | seq;
    }
}

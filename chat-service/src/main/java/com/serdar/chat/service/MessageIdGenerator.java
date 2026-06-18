package com.serdar.chat.service;

import java.util.concurrent.atomic.AtomicInteger;

/** Time-ordered positive long IDs compatible with proto int64 and JS JSON (safe integer). */
public final class MessageIdGenerator {
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    private MessageIdGenerator() {}

    public static long nextId() {
        long ts = System.currentTimeMillis();
        int seq = SEQ.updateAndGet(i -> (i + 1) & 0x3FF);
        // ts * 1024 + seq stays below Number.MAX_SAFE_INTEGER for decades.
        return ts * 1024L + seq;
    }
}

package com.letchat.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MessageIdGenerator {

    private static final long CUSTOM_EPOCH = 1704067200000L;
    private static final int WORKER_ID_BITS = 5;
    private static final int SEQUENCE_BITS = 7;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final int TIMESTAMP_SHIFT = WORKER_ID_BITS + SEQUENCE_BITS;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence;

    public MessageIdGenerator(@Value("${letchat.message-id.worker-id:0}") long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("Message ID workerId must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("System clock moved backwards while generating a message ID");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;
        return ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << SEQUENCE_BITS)
                | sequence;
    }

    private long waitUntilNextMillis(long timestamp) {
        long current = System.currentTimeMillis();
        while (current <= timestamp) {
            Thread.yield();
            current = System.currentTimeMillis();
        }
        return current;
    }
}

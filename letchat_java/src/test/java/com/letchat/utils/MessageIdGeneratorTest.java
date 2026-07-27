package com.letchat.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageIdGeneratorTest {

    private static final long MAX_JAVASCRIPT_SAFE_INTEGER = 9007199254740991L;

    @Test
    void generatesUniqueIncreasingJavascriptSafeIds() {
        MessageIdGenerator generator = new MessageIdGenerator(3);
        Set<Long> ids = new HashSet<>();
        long previous = -1L;

        for (int index = 0; index < 10000; index++) {
            long id = generator.nextId();
            ids.add(id);
            assertTrue(id > previous);
            assertTrue(id <= MAX_JAVASCRIPT_SAFE_INTEGER);
            previous = id;
        }

        assertEquals(10000, ids.size());
    }
}

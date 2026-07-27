package com.letchat.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureTokenGeneratorTest {

    @Test
    void generatesUrlSafeHighEntropyTokens() {
        SecureTokenGenerator generator = new SecureTokenGenerator();
        Set<String> tokens = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            String token = generator.generate();
            assertEquals(43, token.length());
            assertTrue(token.matches("[A-Za-z0-9_-]+"));
            tokens.add(token);
        }

        assertEquals(100, tokens.size());
    }
}

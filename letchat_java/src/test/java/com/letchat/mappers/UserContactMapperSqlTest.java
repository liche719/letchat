package com.letchat.mappers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserContactMapperSqlTest {

    @Test
    void mutationStatementsUseTheSameAliasAsSharedConditions() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/com/letchat/mappers/UserContactMapper.xml")) {
            assertNotNull(stream);
            String mapper = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(mapper.contains("UPDATE user_contact c"));
            assertTrue(mapper.contains("delete c from user_contact c"));
            assertFalse(mapper.contains("UPDATE user_contact u"));
            assertFalse(mapper.contains("delete u from user_contact u"));
        }
    }
}

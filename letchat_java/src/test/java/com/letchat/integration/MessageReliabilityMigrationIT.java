package com.letchat.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class MessageReliabilityMigrationIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.32")
            .withDatabaseName("letchat")
            .withUsername("letchat")
            .withPassword("letchat");

    @Test
    void flywayCreatesOutboxSequenceCursorAndClientIdempotency() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            assertTrue(tableExists(statement, "chat_message_outbox"));
            assertTrue(tableExists(statement, "chat_session_sequence"));
            assertTrue(tableExists(statement, "chat_delivery_cursor"));
            assertTrue(tableExists(statement, "chat_message_inbox"));
            assertEquals(0, indexCount(statement, "chat_message", "uk_message_fingerprint"));

            statement.executeUpdate("""
                    INSERT INTO chat_message(
                        message_id, client_message_id, sequence_no, session_id, message_type,
                        message_content, send_user_id, send_time, contact_id, contact_type
                    ) VALUES(1, 'client-1', 1, 'session-1', 2, 'hello', 'U1', 1, 'U2', 0)
                    """);

            SQLException duplicate = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO chat_message(
                        message_id, client_message_id, sequence_no, session_id, message_type,
                        message_content, send_user_id, send_time, contact_id, contact_type
                    ) VALUES(2, 'client-1', 2, 'session-1', 2, 'retry', 'U1', 2, 'U2', 0)
                    """));
            assertEquals("23000", duplicate.getSQLState());
        }
    }

    @Test
    void atomicSequenceAllocationRemainsUniqueAcrossDatabaseConnections() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        int requests = 64;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Long>> allocations = new ArrayList<>();
            for (int index = 0; index < requests; index++) {
                allocations.add(() -> allocateSequence("sequence-race"));
            }
            List<Future<Long>> futures = executor.invokeAll(allocations);
            List<Long> values = new ArrayList<>();
            for (Future<Long> future : futures) {
                values.add(future.get());
            }
            values.sort(Long::compareTo);

            assertEquals(requests, values.stream().distinct().count());
            for (int index = 0; index < requests; index++) {
                assertEquals(index + 1L, values.get(index));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private long allocateSequence(String sessionId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO chat_session_sequence(session_id, last_sequence)
                    VALUES('%s', LAST_INSERT_ID(1))
                    ON DUPLICATE KEY UPDATE last_sequence = LAST_INSERT_ID(last_sequence + 1)
                    """.formatted(sessionId));
            try (ResultSet resultSet = statement.executeQuery("SELECT LAST_INSERT_ID()")) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private boolean tableExists(Statement statement, String tableName) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = '%s'
                """.formatted(tableName))) {
            resultSet.next();
            return resultSet.getInt(1) == 1;
        }
    }

    private int indexCount(Statement statement, String tableName, String indexName) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = '%s'
                  AND index_name = '%s'
                """.formatted(tableName, indexName))) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}

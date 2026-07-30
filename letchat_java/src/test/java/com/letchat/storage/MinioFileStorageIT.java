package com.letchat.storage;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class MinioFileStorageIT {

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z")
    )
            .withEnv("MINIO_ROOT_USER", "letchat")
            .withEnv("MINIO_ROOT_PASSWORD", "letchat-secret")
            .withCommand("server", "/data")
            .withExposedPorts(9000);

    @Test
    void storesAndStreamsObjectWithoutUsingLocalDisk() throws Exception {
        MinioFileStorage storage = new MinioFileStorage(
                "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000),
                "letchat",
                "letchat-secret",
                "letchat-it"
        );
        storage.ensureBucket();
        byte[] content = "hello-minio".getBytes(StandardCharsets.UTF_8);

        storage.put(
                "messages/202607/1.txt",
                new ByteArrayInputStream(content),
                content.length,
                "text/plain"
        );

        StoredObject stored = storage.get("messages/202607/1.txt");
        try (var input = stored.inputStream()) {
            assertEquals("text/plain", stored.contentType());
            assertArrayEquals(content, input.readAllBytes());
        }
    }
}

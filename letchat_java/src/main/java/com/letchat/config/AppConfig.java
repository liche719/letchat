package com.letchat.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@Data
public class AppConfig {

    @Value("${server.port}")
    private Integer serverPort;

    @Value("${ws.port}")
    private Integer wsPort;

    @Value("${ws.read-idle-seconds:75}")
    private Integer wsReadIdleSeconds;

    @Value("${ws.heartbeat-expire-seconds:90}")
    private Integer wsHeartbeatExpireSeconds;

    @Value("${project.folder}")
    private String projectFolder;

    @Value("${admin.emails}")
    private String adminEmails;

    /**
     * Resolves the configured storage root before passing a path to servlet multipart handling.
     * MultipartFile#transferTo treats relative paths as relative to the servlet temp directory.
     */
    public Path getProjectFolderPath() {
        return Paths.get(projectFolder).toAbsolutePath().normalize();
    }

}

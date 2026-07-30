package com.letchat.event;

public record RobotReplyRequested(String sessionId, String userId, String messageContent) {
}

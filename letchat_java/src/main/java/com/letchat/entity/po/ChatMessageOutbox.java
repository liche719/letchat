package com.letchat.entity.po;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class ChatMessageOutbox {
    private String eventId;
    private String sessionId;
    private String routingKey;
    private String payload;
    private Integer status;
    private String claimToken;
    private LocalDateTime claimedAt;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private String lastError;
}

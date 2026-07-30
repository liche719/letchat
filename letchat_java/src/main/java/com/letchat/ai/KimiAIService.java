package com.letchat.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.letchat.utils.StringTools;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class KimiAIService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_HISTORY_SIZE = 10;

    @Value("${kimi.enabled:false}")
    private Boolean enabled;

    @Value("${kimi.api-key:}")
    private String apiKey;

    @Value("${kimi.base-url:https://api.moonshot.cn/v1/chat/completions}")
    private String baseUrl;

    @Value("${kimi.model:moonshot-v1-8k}")
    private String model;

    private final ObjectMapper objectMapper;
    private final OkHttpClient client;
    private final Map<String, Deque<ObjectNode>> sessionHistories = new ConcurrentHashMap<>();

    public KimiAIService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public String getAiReplyWithContext(String sessionId, String userMessage) {
        if (!Boolean.TRUE.equals(enabled) || StringTools.isEmpty(apiKey)) {
            return "AI 服务暂时不可用，请稍后再试。";
        }

        Deque<ObjectNode> history = sessionHistories.computeIfAbsent(sessionId, ignored -> new ArrayDeque<>());
        synchronized (history) {
            addToHistory(history, message("user", userMessage));
            try {
                ObjectNode requestJson = objectMapper.createObjectNode();
                requestJson.put("model", model);
                requestJson.put("max_tokens", 1024);
                ArrayNode messages = requestJson.putArray("messages");
                history.forEach(messages::add);

                Request request = new Request.Builder()
                        .url(baseUrl)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .post(RequestBody.create(objectMapper.writeValueAsBytes(requestJson), JSON_MEDIA_TYPE))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        log.error("Kimi API request failed, status={}", response.code());
                        return "AI 服务暂时不可用，请稍后再试。";
                    }
                    if (response.body() == null) {
                        return "AI 未返回有效内容。";
                    }
                    String aiReply = parseReply(response.body().string());
                    addToHistory(history, message("assistant", aiReply));
                    return aiReply;
                }
            } catch (Exception exception) {
                log.error("Kimi API request failed", exception);
                return "AI 服务暂时不可用，请稍后再试。";
            }
        }
    }

    public void clearContext(String sessionId) {
        sessionHistories.remove(sessionId);
    }

    private ObjectNode message(String role, String content) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private void addToHistory(Deque<ObjectNode> history, ObjectNode message) {
        while (history.size() >= MAX_HISTORY_SIZE) {
            history.pollFirst();
        }
        history.addLast(message);
    }

    private String parseReply(String json) {
        try {
            JsonNode content = objectMapper.readTree(json)
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");
            return content.isTextual() && !content.asText().isBlank()
                    ? content.asText()
                    : "AI 未返回有效内容。";
        } catch (Exception exception) {
            log.error("Failed to parse Kimi response", exception);
            return "AI 回复解析失败。";
        }
    }
}

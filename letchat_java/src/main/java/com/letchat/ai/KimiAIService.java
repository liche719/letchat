package com.letchat.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class KimiAIService {

    private static final String BASE_URL = "https://api.moonshot.cn/v1/chat/completions";
    private static final String API_KEY = "sk-s0KJfdvQF6XG86A60sGKxBONO35eEkF3Ev0cc08t7OKWpyBc";
    private static final String MODEL = "moonshot-v1-8k";

    // 存储每个用户的对话历史，实际项目中建议使用Redis等外部存储
    private final Map<String, Deque<JSONObject>> sessionHistories = new ConcurrentHashMap<>();

    // 控制最大历史记录数
    private static final int MAX_HISTORY_SIZE = 10;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 调用 Kimi 大模型获取 AI 回复（带上下文版本）
     *
     * @param userId      用户ID，用于区分不同用户的对话
     * @param userMessage 用户输入的消息
     * @return AI 回复内容
     */
    public String getAiReplyWithContext(String userId, String userMessage) {
        // 获取或创建该会话的历史记录
        Deque<JSONObject> history = sessionHistories.computeIfAbsent(userId, k -> new ArrayDeque<>());

        // 添加用户消息到历史记录
        JSONObject userMessageObj = new JSONObject();
        userMessageObj.put("role", "user");
        userMessageObj.put("content", userMessage);
        addToHistory(history, userMessageObj);

        try {
            // 构建请求体 JSON
            JSONArray messages = new JSONArray();
            messages.addAll(history);

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", MODEL);
            requestBody.put("max_tokens", 1024);
            requestBody.put("messages", messages);

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    requestBody.toJSONString()
            );

            Request request = new Request.Builder()
                    .url(BASE_URL)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            Response response = null;
            try {
                response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    log.error("Kimi API 调用失败，状态码: {}", response.code());
                    return "AI 服务暂时不可用，请稍后再试。";
                }

                String responseBody = response.body().string();
                log.debug("Kimi 原始响应: {}", responseBody);

                String aiReply = parseReply(responseBody);

                // 添加AI回复到历史记录
                JSONObject aiMessageObj = new JSONObject();
                aiMessageObj.put("role", "assistant");
                aiMessageObj.put("content", aiReply);
                addToHistory(history, aiMessageObj);

                return aiReply;
            } finally {
                if (response != null && response.body() != null) {
                    response.body().close();
                }
            }
        } catch (Exception e) {
            log.error("调用 Kimi API 时发生异常", e);
            return "抱歉，AI 服务出错了：" + e.getMessage();
        }
    }

    /**
     * 添加消息到历史记录，并控制历史记录大小
     */
    private void addToHistory(Deque<JSONObject> history, JSONObject message) {
        // 如果历史记录已满，移除最旧的一条记录（一问一答为一组）
        if (history.size() >= MAX_HISTORY_SIZE) {
            history.pollFirst(); // 移除最早的用户消息
        }
        history.addLast(message); // 添加新消息
    }

    /**
     * 清除指定会话的历史记录
     */
    public void clearContext(String sessionId) {
        sessionHistories.remove(sessionId);
    }

    /**
     * 解析 Kimi 返回的 JSON，提取回复文本
     */
    private String parseReply(String json) {
        try {
            JSONObject obj = JSON.parseObject(json);
            if (obj.containsKey("choices")) {
                JSONArray choices = obj.getJSONArray("choices");
                if (choices.size() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    if (choice.containsKey("message")) {
                        JSONObject message = choice.getJSONObject("message");
                        if (message.containsKey("content")) {
                            return message.getString("content");
                        }
                    }
                }
            }
            return "Kimi 未返回有效内容。";
        } catch (Exception e) {
            log.error("解析 Kimi 响应失败", e);
            return "AI 回复解析失败。";
        }
    }
}

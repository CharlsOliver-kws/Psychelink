package com.psychic.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 心理状态识别服务 - 意图判断与风险评估
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PsychologicalService {

    private final WebClient webClient;
    private static final List<String> HIGH_RISK_KEYWORDS = Arrays.asList(
            "自杀", "自残", "想死", "不想活", "结束生命", "活着没意义", "割腕", "跳楼", "服毒", "轻生"
    );

    /**
     * 意图判别 - CHAT/CONSULT/RISK
     */
    public String detectIntent(String message) {
        if (hasHighRiskSignal(message)) {
            return "RISK";
        }
        try {
            String result = callAi("请判断用户消息的意图，返回一个词：CHAT表示普通闲聊/日常问题/问食物等，CONSULT表示明确的心理咨询/情绪问题/抑郁相关，RISK表示自杀/自残等危险想法。只需返回一个词。", "消息: " + message);
            result = result.trim().toUpperCase();
            if (result.contains("RISK")) return "RISK";
            if (result.contains("CONSULT")) return "CONSULT";
            return "CHAT";
        } catch (Exception e) {
            log.warn("Intent detection failed, using rules: {}", e.getMessage());
            if (hasHighRiskSignal(message)) {
                return "RISK";
            }
            // 增强规则：覆盖更多心理咨询相关的关键词
            if (message.contains("心情") || message.contains("难过") || message.contains("抑郁")
                || message.contains("焦虑") || message.contains("痛苦") || message.contains("失眠")
                || message.contains("压力") || message.contains("绝望") || message.contains("害怕")) {
                return "CONSULT";
            }
            return "CHAT";
        }
    }

    /**
     * 风险评估
     */
    public String assessRisk(String message) {
        if (hasHighRiskSignal(message)) {
            return "HIGH";
        }
        try {
            String result = callAi("Assess risk level: NONE, LOW, MEDIUM, or HIGH. Return only one word.", "Message: " + message);
            result = result.trim().toUpperCase();
            if (result.contains("HIGH")) return "HIGH";
            if (result.contains("MEDIUM")) return "MEDIUM";
            if (result.contains("LOW")) return "LOW";
            return "NONE";
        } catch (Exception e) {
            log.warn("Risk assessment failed: {}", e.getMessage());
            return "LOW";
        }
    }

    public boolean hasHighRiskSignal(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return HIGH_RISK_KEYWORDS.stream().anyMatch(message::contains);
    }

    private String callAi(String systemPrompt, String userMessage) {
        Map<String, Object> requestBody = Map.of(
            "model", "glm-4.5-air",
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            )
        );

        Map<?, ?> response = webClient.post()
            .uri("/chat/completions")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(60))
            .block();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices != null && !choices.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return (String) msg.get("content");
        }
        return "";
    }
}

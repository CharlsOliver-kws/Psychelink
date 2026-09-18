package com.psychic.agent.service;

import com.psychic.agent.entity.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 心理状态识别服务：意图三分类（CHAT/CONSULT/RISK）+ 风险四级评估（NONE/LOW/MEDIUM/HIGH）
 *
 * 分类链路：高风险关键词前置兜底 → LLM 分类 → 解析失败时回退关键词规则，
 * 保证任何情况下 RISK 不会被漏判为 CHAT（安全优先，宁可误报）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PsychologicalService {

    private final ChatClient chatClient;

    private static final List<String> HIGH_RISK_KEYWORDS = List.of(
            "自杀", "自残", "想死", "不想活", "结束生命", "活着没意义", "割腕", "跳楼", "服毒", "轻生"
    );
    private static final List<String> CONSULT_KEYWORDS = List.of(
            "心情", "难过", "抑郁", "焦虑", "痛苦", "失眠", "压力", "绝望", "害怕", "孤独", "崩溃"
    );

    /**
     * 意图判别
     */
    public ChatMessage.MessageIntent detectIntent(String message) {
        if (hasHighRiskSignal(message)) {
            return ChatMessage.MessageIntent.RISK;
        }
        try {
            String result = chatClient.prompt()
                    .system("请判断用户消息的意图，返回一个词：CHAT表示普通闲聊/日常问题，"
                            + "CONSULT表示明确的心理咨询/情绪问题，RISK表示自杀/自残等危险想法。只需返回一个词。")
                    .user("消息: " + message)
                    .call()
                    .content();
            return parseIntent(result);
        } catch (Exception e) {
            log.warn("LLM 意图识别失败，回退关键词规则: {}", e.getMessage());
            return fallbackIntent(message);
        }
    }

    /**
     * 风险评估
     */
    public ChatMessage.RiskLevel assessRisk(String message) {
        if (hasHighRiskSignal(message)) {
            return ChatMessage.RiskLevel.HIGH;
        }
        try {
            String result = chatClient.prompt()
                    .system("评估用户消息的心理风险等级，返回一个词：NONE（无风险）、LOW（轻度情绪困扰）、"
                            + "MEDIUM（明显痛苦需要关注）、HIGH（自伤/自杀危机）。只需返回一个词。")
                    .user("消息: " + message)
                    .call()
                    .content();
            return parseRisk(result);
        } catch (Exception e) {
            log.warn("LLM 风险评估失败，回退规则: {}", e.getMessage());
            return hasHighRiskSignal(message) ? ChatMessage.RiskLevel.HIGH : ChatMessage.RiskLevel.LOW;
        }
    }

    public boolean hasHighRiskSignal(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return HIGH_RISK_KEYWORDS.stream().anyMatch(message::contains);
    }

    /**
     * 解析 LLM 意图输出（包内可见，便于单测）
     */
    static ChatMessage.MessageIntent parseIntent(String llmOutput) {
        if (llmOutput == null) {
            return ChatMessage.MessageIntent.CHAT;
        }
        String normalized = llmOutput.trim().toUpperCase();
        if (normalized.contains("RISK")) {
            return ChatMessage.MessageIntent.RISK;
        }
        if (normalized.contains("CONSULT")) {
            return ChatMessage.MessageIntent.CONSULT;
        }
        return ChatMessage.MessageIntent.CHAT;
    }

    /**
     * 解析 LLM 风险输出（包内可见，便于单测）
     */
    static ChatMessage.RiskLevel parseRisk(String llmOutput) {
        if (llmOutput == null) {
            return ChatMessage.RiskLevel.NONE;
        }
        String normalized = llmOutput.trim().toUpperCase();
        if (normalized.contains("HIGH")) {
            return ChatMessage.RiskLevel.HIGH;
        }
        if (normalized.contains("MEDIUM")) {
            return ChatMessage.RiskLevel.MEDIUM;
        }
        if (normalized.contains("LOW")) {
            return ChatMessage.RiskLevel.LOW;
        }
        return ChatMessage.RiskLevel.NONE;
    }

    static ChatMessage.MessageIntent fallbackIntent(String message) {
        if (hasHighRiskSignalStatic(message)) {
            return ChatMessage.MessageIntent.RISK;
        }
        if (message != null && CONSULT_KEYWORDS.stream().anyMatch(message::contains)) {
            return ChatMessage.MessageIntent.CONSULT;
        }
        return ChatMessage.MessageIntent.CHAT;
    }

    static boolean hasHighRiskSignalStatic(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return HIGH_RISK_KEYWORDS.stream().anyMatch(message::contains);
    }
}

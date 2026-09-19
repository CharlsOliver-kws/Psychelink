package io.github.charlsoliver.psychelink.dto;

import io.github.charlsoliver.psychelink.entity.ChatMessage;

import java.time.LocalDateTime;

/**
 * 聊天接口 DTO（响应侧）
 *
 * 历史记录只暴露必要字段：不序列化 User 实体（含密码哈希）、
 * RAG 内部上下文与 token 统计等实现细节。
 */
public final class ChatDtos {
    private ChatDtos() {
    }

    public record ChatHistoryResponse(
            Long id,
            String userMessage,
            String aiResponse,
            ChatMessage.MessageIntent intent,
            ChatMessage.RiskLevel riskLevel,
            LocalDateTime createdAt) {

        public static ChatHistoryResponse from(ChatMessage message) {
            return new ChatHistoryResponse(
                    message.getId(),
                    message.getUserMessage(),
                    message.getAiResponse(),
                    message.getIntent(),
                    message.getRiskLevel(),
                    message.getCreatedAt());
        }
    }
}

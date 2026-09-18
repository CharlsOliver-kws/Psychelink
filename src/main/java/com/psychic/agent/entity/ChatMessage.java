package com.psychic.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天消息实体
 */
@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String userMessage;

    @Column(columnDefinition = "TEXT")
    private String aiResponse;

    @Enumerated(EnumType.STRING)
    private MessageIntent intent; // CHAT, CONSULT, RISK

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel; // LOW, MEDIUM, HIGH

    @Column(columnDefinition = "TEXT")
    private String context; // RAG 检索的上下文

    private Integer tokens;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * 消息意图类型
     */
    public enum MessageIntent {
        CHAT,      // 非心理相关闲聊
        CONSULT,   // 心理咨询
        RISK       // 高风险问题
    }

    /**
     * 风险等级
     */
    public enum RiskLevel {
        NONE,      // 无风险
        LOW,       // 低风险
        MEDIUM,    // 中风险
        HIGH       // 高风险
    }
}
package io.github.charlsoliver.psychelink.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 风险事件实体 —— 「识别 → 记录 → 人工复核」闭环的数据载体
 */
@Entity
@Table(name = "risk_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, length = 20)
    private String intent; // CONSULT / RISK

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatMessage.RiskLevel riskLevel; // LOW / MEDIUM / HIGH

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private Boolean emailSent = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status = ReviewStatus.OPEN;

    /** 复核人（管理员） */
    private String reviewer;

    /** 复核意见 */
    @Column(columnDefinition = "TEXT")
    private String reviewNote;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * 复核状态：待复核 → 已复核
     */
    public enum ReviewStatus {
        OPEN,      // 待复核
        REVIEWED   // 已人工复核
    }
}

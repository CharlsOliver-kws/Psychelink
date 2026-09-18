package com.psychic.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 心理报告实体
 */
@Entity
@Table(name = "psychological_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PsychologicalReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    @Column(columnDefinition = "TEXT")
    private String summary; // 对话摘要

    @Column(columnDefinition = "TEXT")
    private String analysis; // 心理分析

    @Column(columnDefinition = "TEXT")
    private String suggestions; // 建议措施

    private Boolean alertSent = false;

    private Boolean excelRecorded = false;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum RiskLevel {
        NONE,      // 无风险
        LOW,       // 低风险
        MEDIUM,    // 中风险
        HIGH       // 高风险
    }
}
package io.github.charlsoliver.psychelink.dto;

import io.github.charlsoliver.psychelink.entity.ChatMessage;
import io.github.charlsoliver.psychelink.entity.RiskEvent;

import java.time.LocalDateTime;

/**
 * 管理端接口 DTO（响应侧）
 */
public final class AdminDtos {
    private AdminDtos() {
    }

    public record RiskEventResponse(
            Long id,
            String username,
            String intent,
            ChatMessage.RiskLevel riskLevel,
            String message,
            boolean emailSent,
            RiskEvent.ReviewStatus status,
            String reviewer,
            String reviewNote,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt) {

        public static RiskEventResponse from(RiskEvent event) {
            return new RiskEventResponse(
                    event.getId(),
                    event.getUsername(),
                    event.getIntent(),
                    event.getRiskLevel(),
                    event.getMessage(),
                    Boolean.TRUE.equals(event.getEmailSent()),
                    event.getStatus(),
                    event.getReviewer(),
                    event.getReviewNote(),
                    event.getReviewedAt(),
                    event.getCreatedAt());
        }
    }
}

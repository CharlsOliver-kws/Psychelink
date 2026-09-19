package io.github.charlsoliver.psychelink.service;

import io.github.charlsoliver.psychelink.entity.ChatMessage;
import io.github.charlsoliver.psychelink.entity.RiskEvent;
import io.github.charlsoliver.psychelink.repository.RiskEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 风险事件服务 —— 「识别 → 记录 → 人工复核」闭环
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEventService {

    private final RiskEventRepository riskEventRepository;

    @Transactional
    public RiskEvent record(String username, String message, ChatMessage.MessageIntent intent,
                            ChatMessage.RiskLevel riskLevel, boolean emailSent) {
        RiskEvent event = new RiskEvent();
        event.setUsername(username);
        event.setMessage(message);
        event.setIntent(intent.name());
        event.setRiskLevel(riskLevel);
        event.setEmailSent(emailSent);
        event.setStatus(RiskEvent.ReviewStatus.OPEN);
        RiskEvent saved = riskEventRepository.save(event);
        log.info("风险事件已记录: id={}, user={}, risk={}, emailSent={}",
                saved.getId(), username, riskLevel, emailSent);
        return saved;
    }

    public List<RiskEvent> list(RiskEvent.ReviewStatus status) {
        return status == null
                ? riskEventRepository.findTop200ByOrderByCreatedAtDesc()
                : riskEventRepository.findTop200ByStatusOrderByCreatedAtDesc(status);
    }

    public Optional<RiskEvent> findById(Long id) {
        return riskEventRepository.findById(id);
    }

    /**
     * 人工复核：管理员确认事件并填写处理意见
     */
    @Transactional
    public Optional<RiskEvent> review(Long id, String reviewer, String note) {
        return riskEventRepository.findById(id).map(event -> {
            event.setStatus(RiskEvent.ReviewStatus.REVIEWED);
            event.setReviewer(reviewer);
            event.setReviewNote(note);
            event.setReviewedAt(java.time.LocalDateTime.now());
            return riskEventRepository.save(event);
        });
    }
}

package com.psychic.agent.controller;

import com.psychic.agent.dto.AuthDtos.ReviewRequest;
import com.psychic.agent.entity.RiskEvent;
import com.psychic.agent.service.RiskEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端控制器（仅 ROLE_ADMIN）：风险事件列表 + 人工复核
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final RiskEventService riskEventService;

    /**
     * 风险事件列表，可选按复核状态过滤（OPEN / REVIEWED）
     */
    @GetMapping("/risk-events")
    public List<RiskEvent> listRiskEvents(
            @RequestParam(required = false) RiskEvent.ReviewStatus status) {
        return riskEventService.list(status);
    }

    /**
     * 人工复核：确认事件并填写处理意见，完成「识别 → 记录 → 复核」闭环
     */
    @PutMapping("/risk-events/{id}/review")
    public ResponseEntity<?> review(@PathVariable Long id,
                                    @RequestBody(required = false) ReviewRequest request,
                                    Authentication authentication) {
        String note = request == null ? null : request.note();
        return riskEventService.review(id, authentication.getName(), note)
                .<ResponseEntity<?>>map(event -> ResponseEntity.ok(event))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "风险事件不存在: " + id)));
    }
}

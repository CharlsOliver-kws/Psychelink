package io.github.charlsoliver.psychelink.controller;

import io.github.charlsoliver.psychelink.dto.AdminDtos.RiskEventResponse;
import io.github.charlsoliver.psychelink.dto.AuthDtos.ReviewRequest;
import io.github.charlsoliver.psychelink.entity.RiskEvent;
import io.github.charlsoliver.psychelink.exception.NotFoundException;
import io.github.charlsoliver.psychelink.service.RiskEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public List<RiskEventResponse> listRiskEvents(
            @RequestParam(required = false) RiskEvent.ReviewStatus status) {
        return riskEventService.list(status).stream()
                .map(RiskEventResponse::from)
                .toList();
    }

    /**
     * 人工复核：确认事件并填写处理意见，完成「识别 → 记录 → 复核」闭环
     */
    @PutMapping("/risk-events/{id}/review")
    public RiskEventResponse review(@PathVariable Long id,
                                    @RequestBody(required = false) ReviewRequest request,
                                    Authentication authentication) {
        String note = request == null ? null : request.note();
        RiskEvent event = riskEventService.review(id, authentication.getName(), note)
                .orElseThrow(() -> new NotFoundException("风险事件不存在: " + id));
        return RiskEventResponse.from(event);
    }
}

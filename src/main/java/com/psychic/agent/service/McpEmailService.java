package com.psychic.agent.service;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 风险预警邮件服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${app.alert.recipient:}")
    private String alertRecipient;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @WithSpan("mcp_email")
    public void sendAlertEmail(String username, String message) {
        log.info("========== 发送风险预警 ==========");
        log.info("接收人: {}", alertRecipient);
        log.info("用户名: {}", username);
        log.info("风险消息: {}", message);

        if (alertRecipient == null || alertRecipient.isBlank()) {
            log.warn("未配置 app.alert.recipient（ALERT_RECIPIENT 环境变量），跳过预警邮件");
            return;
        }

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(mailFrom);
            mail.setTo(alertRecipient);
            mail.setSubject("[PsycheLink] 心理风险预警");
            String text = "心理风险预警通知\n\n"
                    + "用户: " + username + "\n"
                    + "时间: " + LocalDateTime.now().format(FORMATTER) + "\n"
                    + "风险内容: " + message + "\n\n"
                    + "请及时关注并采取干预措施。";
            mail.setText(text);

            mailSender.send(mail);
            log.info("[完成] 预警邮件发送成功");
        } catch (Exception e) {
            log.error("[错误] 邮件发送失败: {}", e.getMessage());
        }
    }
}

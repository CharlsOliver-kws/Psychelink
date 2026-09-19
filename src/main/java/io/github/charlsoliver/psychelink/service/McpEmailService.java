package io.github.charlsoliver.psychelink.service;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 邮件预警工具 —— 同时通过 MCP Server 暴露给外部 AI 客户端（Claude Desktop 等）
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

    /**
     * 发送心理风险预警邮件（内部确定性调用）
     *
     * @return 邮件是否实际发出（未配置收件人或发送失败返回 false，调用方据此记录事件状态）
     */
    @WithSpan("mcp_email")
    public boolean sendAlertEmail(String username, String message) {
        log.info("发送风险预警: user={}", username);

        if (alertRecipient == null || alertRecipient.isBlank()) {
            log.warn("未配置 app.alert.recipient（ALERT_RECIPIENT 环境变量），跳过预警邮件");
            return false;
        }

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(mailFrom);
            mail.setTo(alertRecipient);
            mail.setSubject("[PsycheLink] 心理风险预警");
            mail.setText("心理风险预警通知\n\n"
                    + "用户: " + username + "\n"
                    + "时间: " + LocalDateTime.now().format(FORMATTER) + "\n"
                    + "风险内容: " + message + "\n\n"
                    + "请及时关注并采取干预措施。");
            mailSender.send(mail);
            log.info("预警邮件发送成功");
            return true;
        } catch (Exception e) {
            log.error("邮件发送失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * MCP 工具：发送心理风险预警邮件（供外部 MCP 客户端调用）
     */
    @Tool(description = "发送心理风险预警邮件给管理员/辅导员，在发现用户存在心理危机风险时调用")
    public String sendAlertEmailTool(
            @ToolParam(description = "用户名") String username,
            @ToolParam(description = "触发预警的消息内容") String message) {
        boolean sent = sendAlertEmail(username, message);
        return sent
                ? "预警邮件已发送（接收人: " + alertRecipient + "）"
                : "预警邮件未能发送（未配置收件人或 SMTP 失败），请检查服务端日志";
    }
}

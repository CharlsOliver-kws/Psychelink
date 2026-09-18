package com.psychic.agent.config;

import com.psychic.agent.service.McpEmailService;
import com.psychic.agent.service.McpExcelService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 配置：将邮件预警与 Excel 台账工具通过 MCP 协议暴露，
 * 供 Claude Desktop 等外部 MCP 客户端复用（见 docs/architecture.md）
 */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider psychelinkTools(McpEmailService emailService, McpExcelService excelService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(emailService, excelService)
                .build();
    }
}

package com.psychic.agent.config;

import org.springframework.context.annotation.Configuration;

/**
 * 工具开关配置（预警收件人等外部配置见 application.properties / 环境变量）
 */
@Configuration
public class McpConfig {

    public boolean isExcelEnabled() {
        return true;
    }

    public boolean isEmailEnabled() {
        return true;
    }
}

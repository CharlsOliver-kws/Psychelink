package com.psychic.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.extern.slf4j.Slf4j;

/**
 * WebClient 配置
 */
@Configuration
@Slf4j
public class WebClientConfig {

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Bean
    public WebClient webClient() {
        String sanitizedBaseUrl = sanitizeBaseUrl(baseUrl);
        return WebClient.builder()
                .baseUrl(sanitizedBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json; charset=UTF-8")
                .build();
    }

    private String sanitizeBaseUrl(String rawBaseUrl) {
        String cleaned = rawBaseUrl == null ? "" : rawBaseUrl.trim().replace("`", "");
        // 当前代码走 OpenAI Chat Completions 协议，如果误填 Anthropc 网关则自动纠正到 OpenAI 兼容地址。
        if (cleaned.contains("/api/anthropic")) {
            log.warn("检测到 Anthropc 协议地址，已自动切换为 OpenAI 兼容地址");
            return "https://open.bigmodel.cn/api/paas/v4";
        }
        return cleaned;
    }
}

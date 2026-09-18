package com.psychic.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psychic.agent.entity.ChatMessage;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 聊天服务 - 核心业务逻辑：意图分流、RAG检索、风险预警、流式响应
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final WebClient webClient;
    private final PsychologicalService psychologicalService;
    private final McpEmailService mcpEmailService;
    private final McpExcelService mcpExcelService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 处理用户消息并返回响应（非流式）
     */
    public Mono<String> chat(String message, String username) {
        log.info("========== 收到用户消息 ==========");
        log.info("用户: {}, 消息: {}", username, message);

        // 1. 意图识别
        String intent = psychologicalService.detectIntent(message);
        if (psychologicalService.hasHighRiskSignal(message)) {
            intent = "RISK";
        }
        log.info("意图识别结果: {}", intent);

        // 2. 风险评估 (用于后续存档和预警)
        String riskLevelStr = psychologicalService.assessRisk(message);
        if ("RISK".equals(intent) && !"HIGH".equals(riskLevelStr)) {
            riskLevelStr = "HIGH";
        }
        ChatMessage.RiskLevel riskLevel = ChatMessage.RiskLevel.valueOf(riskLevelStr);

        // 3. 分支处理
        if ("CHAT".equals(intent)) {
            // 分支1: 普通闲聊 -> 直接返回，不走 RAG
            log.info("[流程] 进入闲聊模式");
            return getAiResponse(
                    "你的名字叫肉包，是用户的聊天伙伴。请用自然、温暖的语气与用户闲聊。",
                    message
            );
        } else {
            // 分支2: 心理咨询/风险干预 -> 走 RAG + 预警存档
            log.info("[流程] 进入咨询/干预模式");

            // A. RAG 检索 (获取专业知识上下文)
            String context = knowledgeBaseService.retrieve(message);
            log.info("RAG 检索结果长度: {}", context != null ? context.length() : 0);

            // B. 风险预警与存档 (异步执行，不阻塞响应)
            handleRiskAndArchiveAsync(username, message, riskLevel);

            // C. 构建提示词并返回
            // 肉包角色设定
            String roleSetting = "你叫肉包，是一位拥有 10 年经验的专业心理咨询专家，擅长共情、引导和危机干预。你正在PsycheLink平台上为用户提供心理支持。你的风格是温暖、自然，像朋友的聊天那样亲切。";

            // 风险处理
            String riskPrompt = "";
            if ("RISK".equals(intent)) {
                riskPrompt = "【紧急】用户处于高风险状态（自伤/自杀倾向）。请立即进行危机干预，优先确保用户安全，使用温暖、坚定的语气鼓励用户寻求专业帮助。";
            }

            return getAiResponseWithRagPrompt(roleSetting, riskPrompt, message, context);
        }
    }

    /**
     * 异步处理风险预警与存档
     */
    private void handleRiskAndArchiveAsync(String username, String message, ChatMessage.RiskLevel riskLevel) {
        Mono.fromRunnable(() -> handleRiskAndArchive(username, message, riskLevel))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    /**
     * 处理风险预警与数据存档
     */
    private void handleRiskAndArchive(String username, String message, ChatMessage.RiskLevel riskLevel) {
        // 1. 写入 Excel 存档 (低/中/高风险均记录)
        try {
            com.psychic.agent.entity.User user = new com.psychic.agent.entity.User();
            user.setUsername(username != null ? username : "Anonymous");
            mcpExcelService.recordPsychologicalData(user, riskLevel, message);
        } catch (Exception e) {
            log.error("Excel 存档失败", e);
        }

        // 2. 高风险发送邮件预警
        if (riskLevel == ChatMessage.RiskLevel.HIGH || riskLevel == ChatMessage.RiskLevel.MEDIUM) {
            try {
                mcpEmailService.sendAlertEmail(username != null ? username : "Anonymous", message);
            } catch (Exception e) {
                log.error("邮件预警发送失败", e);
            }
        }
    }

    /**
     * 调用 AI (无上下文)
     */
    private Mono<String> getAiResponse(String systemPrompt, String userMessage) {
        return getAiResponseWithContext(systemPrompt, userMessage, null);
    }

    /**
     * 调用 AI - 使用重构的RAG提示词模板
     */
    @WithSpan("prompt_assembly")
    public Mono<String> getAiResponseWithRagPrompt(String roleSetting, String riskPrompt, String userMessage, String context) {
        // 构建完整的system prompt
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("## [Role]\n");
        systemPrompt.append(roleSetting).append("\n\n");

        systemPrompt.append("## [Knowledge Context]\n");
        systemPrompt.append("以下是从专业心理知识库中检索到的相关参考信息：\n");
        systemPrompt.append("---\n");
        if (context != null && !context.isEmpty()) {
            systemPrompt.append(context);
        } else {
            systemPrompt.append("（无相关知识）");
        }
        systemPrompt.append("\n---\n");
        systemPrompt.append("*注意：仅参考上述知识中与用户问题相关的部分。如果知识库中没有相关信息，请基于专业的心理学原则回答。*\n\n");

        systemPrompt.append("## [Output Constraints]\n");
        systemPrompt.append("请遵循以下回答规范：\n");
        systemPrompt.append("1. **情感共情**：首先对用户的情绪表达认可和共情，严禁使用说教式语气。\n");
        systemPrompt.append("2. **专业引导**：结合知识上下文中的理论，给出1-2条具体的调节建议。\n");
        systemPrompt.append("3. **危机干预**：如果用户出现自伤、自杀等危机信号，请优先采用危机干预话术，并引导其寻求线下专业医疗帮助。\n");
        systemPrompt.append("4. **简洁流畅**：回复字数控制在200字以内，保持对话的自然感。\n");
        systemPrompt.append("5. **打字机样式**：用普通段落文字回复，禁止使用格式标记。\n\n");

        if (riskPrompt != null && !riskPrompt.isEmpty()) {
            systemPrompt.append("## [Important]\n").append(riskPrompt).append("\n");
        }

        log.info("===== 重构后的Prompt =====");
        log.info(systemPrompt.toString());
        log.info("==========================");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt.toString()));
        messages.add(Map.of("role", "user", "content", "## [User Input]\n用户当前的情感诉求：\n> " + userMessage));

        // 非流式调用
        Map<String, Object> requestBody = Map.of(
            "model", "glm-4.5-air",
            "messages", messages,
            "stream", false
        );

        log.debug("调用 AI API: model=glm-4.5-air, userMessage={}", userMessage);

        return webClient.post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .doOnNext(response -> log.info("AI 响应: {}", response))
            .flatMap(response -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resp = objectMapper.readValue(response, Map.class);

                    if (resp.containsKey("error")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> error = (Map<String, Object>) resp.get("error");
                        log.error("智谱 API 错误: {}", error);
                        return Mono.just("抱歉，AI 服务返回错误: " + error.get("message"));
                    }

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> firstChoice = choices.get(0);
                        if (firstChoice != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
                            if (message != null) {
                                String content = (String) message.get("content");
                                if (content != null && !content.isEmpty()) {
                                    log.info(">>> 返回内容: {}", content.substring(0, Math.min(50, content.length())));
                                    return Mono.just(content);
                                }
                            }
                        }
                    }
                    return Mono.just("抱歉，AI 未返回有效内容");
                } catch (Exception e) {
                    log.error(">>> 解析异常: {}", e.getMessage(), e);
                    return Mono.just("抱歉，AI 响应解析失败");
                }
            })
            .timeout(Duration.ofSeconds(120))
            .onErrorResume(e -> {
                log.error("AI 调用异常: {} | {}", e.getClass().getSimpleName(), e.getMessage(), e);
                return Mono.just("抱歉，AI 服务响应超时或异常，请稍后重试。");
            });
    }

    /**
     * 调用 AI (带 RAG 上下文) - 非流式
     */
    private Mono<String> getAiResponseWithContext(String systemPrompt, String userMessage, String context) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        if (context != null && !context.isEmpty()) {
            messages.add(Map.of("role", "user", "content", "参考知识:\n" + context));
        }

        messages.add(Map.of("role", "user", "content", userMessage));

        // 非流式调用
        Map<String, Object> requestBody = Map.of(
            "model", "glm-4.5-air",
            "messages", messages,
            "stream", false
        );

        log.debug("调用 AI API: model=glm-4.5-air, userMessage={}", userMessage);

        return webClient.post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .doOnNext(response -> log.info("AI 响应: {}", response))
            .flatMap(response -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resp = objectMapper.readValue(response, Map.class);

                    if (resp.containsKey("error")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> error = (Map<String, Object>) resp.get("error");
                        log.error("智谱 API 错误: {}", error);
                        return Mono.just("抱歉，AI 服务返回错误: " + error.get("message"));
                    }

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> firstChoice = choices.get(0);
                        if (firstChoice != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
                            if (message != null) {
                                String content = (String) message.get("content");
                                if (content != null && !content.isEmpty()) {
                                    log.info(">>> 返回内容: {}", content.substring(0, Math.min(50, content.length())));
                                    return Mono.just(content);
                                }
                            }
                        }
                    }
                    return Mono.just("抱歉，AI 未返回有效内容");
                } catch (Exception e) {
                    log.error(">>> 解析异常: {}", e.getMessage(), e);
                    return Mono.just("抱歉，AI 响应解析失败");
                }
            })
            .timeout(Duration.ofSeconds(120))
            .onErrorResume(e -> {
                log.error("AI 调用异常: {} | {}", e.getClass().getSimpleName(), e.getMessage(), e);
                return Mono.just("抱歉，AI 服务响应超时或异常，请稍后重试。");
            });
    }
}

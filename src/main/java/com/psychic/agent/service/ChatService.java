package com.psychic.agent.service;

import com.psychic.agent.entity.ChatMessage;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * 聊天服务 —— 核心编排：意图分流 → RAG 检索 → 流式生成 → 记录/预警
 *
 * 预警动作（邮件、风险事件入库）由代码确定性触发，不交给模型自由决定——
 * 安关键路径不能依赖模型的工具调用意愿。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatClient chatClient;
    private final PsychologicalService psychologicalService;
    private final McpEmailService mcpEmailService;
    private final McpExcelService mcpExcelService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final RiskEventService riskEventService;
    private final ChatHistoryService chatHistoryService;

    /**
     * 处理用户消息，以 SSE 流式返回（首 Token 即推送，亚秒级感知响应）
     */
    public Flux<String> chatStream(String message, String username) {
        log.info("收到消息: user={}, length={}", username, message.length());

        // 1. 预处理（意图识别 + 风险分级 + RAG 检索）在弹性线程池执行，避免阻塞
        return Mono.fromCallable(() -> preprocess(message, username))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(ctx -> {
                    StringBuilder replyBuffer = new StringBuilder();
                    return streamResponse(ctx, message, username)
                            .doOnNext(replyBuffer::append)
                            .doOnComplete(() ->
                                    persist(ctx, message, username, replyBuffer.toString()));
                });
    }

    /**
     * 预处理：产出对话上下文
     */
    private ChatContext preprocess(String message, String username) {
        ChatMessage.MessageIntent intent = psychologicalService.detectIntent(message);
        ChatMessage.RiskLevel riskLevel = psychologicalService.assessRisk(message);

        // 安全兜底：关键词命中强制 RISK/HIGH，LLM 只能调高不能调低
        if (psychologicalService.hasHighRiskSignal(message)) {
            intent = ChatMessage.MessageIntent.RISK;
            riskLevel = ChatMessage.RiskLevel.HIGH;
        }
        if (intent == ChatMessage.MessageIntent.RISK && riskLevel != ChatMessage.RiskLevel.HIGH) {
            riskLevel = ChatMessage.RiskLevel.HIGH;
        }

        // 风险预警与记录（异步，不阻塞流式响应）
        if (intent != ChatMessage.MessageIntent.CHAT && riskLevel != ChatMessage.RiskLevel.NONE) {
            handleRiskAsync(username, message, intent, riskLevel);
        }

        // RAG 检索（仅 CONSULT/RISK）
        String context = intent == ChatMessage.MessageIntent.CHAT
                ? ""
                : knowledgeBaseService.retrieve(message);
        log.info("意图={}, 风险={}, RAG上下文长度={}", intent, riskLevel, context.length());

        return new ChatContext(intent, riskLevel, context);
    }

    /**
     * 构建 Prompt 并流式生成
     */
    @WithSpan("prompt_assembly")
    private Flux<String> streamResponse(ChatContext ctx, String message, String username) {
        String systemPrompt = buildSystemPrompt(ctx);
        String userPrompt = "## [User Input]\n用户当前的情感诉求：\n> " + message;

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .content()
                .timeout(Duration.ofSeconds(120))
                .doOnNext(chunk -> log.debug("Token 推送: user={}, chunk={}", username, chunk))
                .onErrorResume(e -> {
                    log.error("流式生成异常: {}", e.getMessage(), e);
                    return Flux.just("抱歉，AI 服务暂时不可用，请稍后重试。");
                });
    }

    /**
     * 构建 System Prompt（人设 + 知识上下文 + 输出约束）
     */
    private String buildSystemPrompt(ChatContext ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("## [Role]\n");
        if (ctx.intent() == ChatMessage.MessageIntent.CHAT) {
            sb.append("你的名字叫肉包，是用户的聊天伙伴。请用自然、温暖的语气与用户闲聊。\n");
            return sb.toString();
        }
        sb.append("你叫肉包，是一位拥有 10 年经验的专业心理咨询专家，擅长共情、引导和危机干预。")
          .append("你正在 PsycheLink 平台上为用户提供心理支持。你的风格是温暖、自然，像朋友的聊天那样亲切。\n\n");

        sb.append("## [Knowledge Context]\n");
        sb.append("以下是从专业心理知识库中检索到的相关参考信息：\n---\n");
        sb.append(ctx.context().isEmpty() ? "（无相关知识）" : ctx.context());
        sb.append("\n---\n");
        sb.append("*注意：仅参考上述知识中与用户问题相关的部分。如果知识库中没有相关信息，请基于专业的心理学原则回答。*\n\n");

        sb.append("## [Output Constraints]\n");
        sb.append("请遵循以下回答规范：\n");
        sb.append("1. **情感共情**：首先对用户的情绪表达认可和共情，严禁使用说教式语气。\n");
        sb.append("2. **专业引导**：结合知识上下文中的理论，给出1-2条具体的调节建议。\n");
        sb.append("3. **危机干预**：如果用户出现自伤、自杀等危机信号，请优先采用危机干预话术，并引导其寻求线下专业医疗帮助。\n");
        sb.append("4. **简洁流畅**：回复字数控制在200字以内，保持对话的自然感。\n");
        sb.append("5. **打字机样式**：用普通段落文字回复，禁止使用格式标记。\n\n");

        if (ctx.intent() == ChatMessage.MessageIntent.RISK) {
            sb.append("## [Important]\n");
            sb.append("【紧急】用户处于高风险状态（自伤/自杀倾向）。请立即进行危机干预，优先确保用户安全，")
              .append("使用温暖、坚定的语气鼓励用户寻求专业帮助。\n");
        }
        return sb.toString();
    }

    /**
     * 流结束后持久化对话记录
     */
    private void persist(ChatContext ctx, String message, String username, String reply) {
        try {
            chatHistoryService.save(username, message, reply, ctx.intent(), ctx.riskLevel(), ctx.context());
        } catch (Exception e) {
            log.error("对话记录保存失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 异步风险处理：事件入库（人工复核闭环的起点）+ Excel 台账 + 邮件预警
     */
    private void handleRiskAsync(String username, String message,
                                 ChatMessage.MessageIntent intent, ChatMessage.RiskLevel riskLevel) {
        Mono.fromRunnable(() -> {
                    boolean emailSent = false;
                    if (riskLevel == ChatMessage.RiskLevel.HIGH || riskLevel == ChatMessage.RiskLevel.MEDIUM) {
                        try {
                            mcpEmailService.sendAlertEmail(username, message);
                            emailSent = true;
                        } catch (Exception e) {
                            log.error("邮件预警发送失败", e);
                        }
                    }
                    riskEventService.record(username, message, intent, riskLevel, emailSent);
                    mcpExcelService.recordRiskData(username, riskLevel, message);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    /**
     * 对话上下文（预处理产物）
     */
    record ChatContext(ChatMessage.MessageIntent intent,
                       ChatMessage.RiskLevel riskLevel,
                       String context) {
    }
}

package io.github.charlsoliver.psychelink.controller;

import io.github.charlsoliver.psychelink.dto.AuthDtos.ChatRequest;
import io.github.charlsoliver.psychelink.dto.ChatDtos.ChatHistoryResponse;
import io.github.charlsoliver.psychelink.service.ChatHistoryService;
import io.github.charlsoliver.psychelink.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 聊天控制器：SSE 流式对话 + 对话历史（数据级隔离）
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final ChatHistoryService chatHistoryService;

    /**
     * 流式聊天：SSE 逐 Token 推送，首 Token 即开始渲染
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        String username = authentication.getName();
        return chatService.chatStream(request.message(), username)
                .doOnError(e -> log.error("Chat error: {}", e.getMessage()));
    }

    /**
     * 当前用户的对话历史（每个用户只能看到自己的记录）
     */
    @GetMapping("/history")
    public List<ChatHistoryResponse> history(Authentication authentication,
                                             @RequestParam(defaultValue = "50") int limit) {
        return chatHistoryService
                .getHistory(authentication.getName(), Math.min(Math.max(limit, 1), 200))
                .stream()
                .map(ChatHistoryResponse::from)
                .toList();
    }
}

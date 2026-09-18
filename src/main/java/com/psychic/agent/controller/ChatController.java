package com.psychic.agent.controller;

import com.psychic.agent.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 聊天控制器 - 非流式响应
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;

    /**
     * 聊天接口 - 非流式返回
     */
    @PostMapping("/stream")
    public Mono<String> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String username = request.getOrDefault("username", "Anonymous");
        log.info("Received request from {}: {}", username, message);

        return chatService.chat(message, username)
                .doOnError(e -> log.error("Chat error: {}", e.getMessage()));
    }
}

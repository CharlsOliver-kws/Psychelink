package io.github.charlsoliver.psychelink.service;

import io.github.charlsoliver.psychelink.entity.ChatMessage;
import io.github.charlsoliver.psychelink.entity.User;
import io.github.charlsoliver.psychelink.repository.ChatMessageRepository;
import io.github.charlsoliver.psychelink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 对话历史服务 —— 数据级隔离：普通用户只能读取自己的对话记录
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Transactional
    public void save(String username, String userMessage, String aiResponse,
                     ChatMessage.MessageIntent intent, ChatMessage.RiskLevel riskLevel, String context) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            log.warn("用户不存在，跳过对话记录: {}", username);
            return;
        }
        ChatMessage msg = new ChatMessage();
        msg.setUser(user);
        msg.setUserMessage(userMessage);
        msg.setAiResponse(aiResponse);
        msg.setIntent(intent);
        msg.setRiskLevel(riskLevel);
        msg.setContext(context);
        chatMessageRepository.save(msg);
    }

    /**
     * 查询指定用户最近 limit 条对话（按时间正序返回）；用户不存在时返回空
     */
    public List<ChatMessage> getHistory(String username, int limit) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    Pageable pageable = PageRequest.of(0, Math.max(limit, 1));
                    List<ChatMessage> recent =
                            chatMessageRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
                    Collections.reverse(recent); // 倒序查询后翻转为时间正序
                    return recent;
                })
                .orElse(Collections.emptyList());
    }
}

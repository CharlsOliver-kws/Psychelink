package com.psychic.agent.service;

import com.psychic.agent.entity.ChatMessage;
import com.psychic.agent.entity.User;
import com.psychic.agent.repository.ChatMessageRepository;
import com.psychic.agent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * 查询指定用户的最近对话（按时间正序返回）；用户不存在时返回空
     */
    public List<ChatMessage> getHistory(String username, int limit) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return Collections.emptyList();
        }
        List<ChatMessage> recent = chatMessageRepository.findTop50ByUserIdOrderByCreatedAtDesc(user.getId());
        Collections.reverse(recent);
        return recent.size() > limit ? recent.subList(recent.size() - limit, recent.size()) : recent;
    }
}

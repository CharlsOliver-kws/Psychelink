package com.psychic.agent.repository;

import com.psychic.agent.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<ChatMessage> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    List<ChatMessage> findByRiskLevelNotNullOrderByCreatedAtDesc();
}
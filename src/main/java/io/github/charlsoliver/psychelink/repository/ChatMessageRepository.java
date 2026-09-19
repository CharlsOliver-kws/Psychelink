package io.github.charlsoliver.psychelink.repository;

import io.github.charlsoliver.psychelink.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 按用户查询最近对话（条数由 Pageable 控制，时间倒序）
     */
    List<ChatMessage> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}

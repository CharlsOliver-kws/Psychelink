package com.psychic.agent.config;

import com.psychic.agent.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器 - 项目启动时自动加载知识库
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    public void run(String... args) {
        log.info("========== 开始知识库初始化 ==========");
        int imported = knowledgeBaseService.initializeFromJson();
        log.info("========== 知识库初始化完成，导入 {} 条 ==========", imported);
    }
}

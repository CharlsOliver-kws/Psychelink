package com.psychic.agent.config;

import com.psychic.agent.entity.User;
import com.psychic.agent.repository.UserRepository;
import com.psychic.agent.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 数据初始化器：管理员引导 + 知识库导入
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final KnowledgeBaseService knowledgeBaseService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.kb.auto-import:true}")
    private boolean kbAutoImport;

    @Override
    public void run(String... args) {
        bootstrapAdmin();
        if (kbAutoImport) {
            int imported = knowledgeBaseService.initializeFromJson();
            log.info("知识库初始化完成，导入 {} 条", imported);
        }
    }

    /**
     * 管理员引导：ADMIN_PASSWORD 显式配置则使用之；否则生成随机密码并打印一次
     */
    private void bootstrapAdmin() {
        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }
        String password = (adminPassword == null || adminPassword.isBlank())
                ? UUID.randomUUID().toString().substring(0, 12)
                : adminPassword;

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole("ROLE_ADMIN");
        admin.setEnabled(true);
        userRepository.save(admin);

        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("=================================================================");
            log.warn("已创建管理员账号: {}，随机密码: {}（仅显示这一次，请及时修改）", adminUsername, password);
            log.warn("生产环境请通过 ADMIN_PASSWORD 环境变量显式指定。");
            log.warn("=================================================================");
        } else {
            log.info("已创建管理员账号: {}", adminUsername);
        }
    }
}

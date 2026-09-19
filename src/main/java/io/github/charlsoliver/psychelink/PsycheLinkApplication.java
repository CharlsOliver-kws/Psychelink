package io.github.charlsoliver.psychelink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * 认证完全由 JWT（JwtAuthFilter + JwtService）承担，
 * 排除默认 UserDetailsService 自动装配，避免每次启动生成无用的随机密码账号。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class PsycheLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(PsycheLinkApplication.class, args);
    }
}

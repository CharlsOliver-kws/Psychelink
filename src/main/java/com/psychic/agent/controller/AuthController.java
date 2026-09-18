package com.psychic.agent.controller;

import com.psychic.agent.dto.AuthDtos.AuthResponse;
import com.psychic.agent.dto.AuthDtos.LoginRequest;
import com.psychic.agent.dto.AuthDtos.RegisterRequest;
import com.psychic.agent.entity.User;
import com.psychic.agent.repository.UserRepository;
import com.psychic.agent.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器：注册 / 登录（JWT）
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * 注册（默认 ROLE_USER），成功后直接签发 Token
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        }
        if (request.email() != null && !request.email().isBlank()
                && userRepository.existsByEmail(request.email())) {
            return ResponseEntity.badRequest().body(Map.of("error", "邮箱已被注册"));
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmail(request.email());
        user.setRole("ROLE_USER");
        user.setEnabled(true);
        userRepository.save(user);

        log.info("新用户注册: {}", user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(buildAuthResponse(user));
    }

    /**
     * 登录：校验密码后签发携带角色的 JWT
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.username()).orElse(null);
        if (user == null || !user.getEnabled() || !passwordEncoder.matches(request.password(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "用户名或密码错误"));
        }
        return ResponseEntity.ok(buildAuthResponse(user));
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.issueToken(user.getUsername(), user.getRole());
        return new AuthResponse(token, "Bearer", user.getUsername(), user.getRole(), jwtService.getExpireSeconds());
    }
}

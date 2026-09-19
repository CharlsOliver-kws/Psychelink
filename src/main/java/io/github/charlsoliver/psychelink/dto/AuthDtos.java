package io.github.charlsoliver.psychelink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 认证与聊天接口 DTO
 */
public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 32) String username,
            @NotBlank @Size(min = 6, max = 64) String password,
            String email) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record AuthResponse(String token, String tokenType, String username, String role, long expiresIn) {
    }

    public record ChatRequest(@NotBlank @Size(max = 2000) String message) {
    }

    public record ReviewRequest(String note) {
    }
}

package com.psychic.agent.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 签发/解析单测
 */
class JwtServiceTest {

    private static final String SECRET = "psychelink-test-secret-0123456789-0123456789";
    private final JwtService jwtService = new JwtService(SECRET, 24);

    @Test
    void issueTokenShouldCarryUsernameAndRole() {
        String token = jwtService.issueToken("alice", "ROLE_USER");

        Claims claims = jwtService.parse(token);
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_USER");
    }

    @Test
    void parseShouldRejectGarbageToken() {
        assertThat(jwtService.parse("not-a-token")).isNull();
        assertThat(jwtService.parse("")).isNull();
        assertThat(jwtService.parse(null)).isNull();
    }

    @Test
    void parseShouldRejectTokenSignedWithDifferentKey() {
        JwtService other = new JwtService("another-secret-key-0123456789-0123456789-abc", 24);
        String forged = other.issueToken("mallory", "ROLE_ADMIN");

        assertThat(jwtService.parse(forged)).isNull();
    }

    @Test
    void parseShouldRejectExpiredToken() {
        // 负有效期 → 签发即过期
        JwtService justExpired = new JwtService(SECRET, -1);

        String token = justExpired.issueToken("bob", "ROLE_USER");
        assertThat(jwtService.parse(token)).isNull();
    }

    @Test
    void expireSecondsShouldMatchConfiguration() {
        assertThat(jwtService.getExpireSeconds()).isEqualTo(24 * 3600);
    }
}

package com.psychic.agent.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证与 RBAC 集成测试：注册 → 登录 → 角色隔离
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerShouldReturnTokenAndRole() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.get("role").asText()).isEqualTo("ROLE_USER");
        assertThat(body.get("username").asText()).isEqualTo("newuser");
    }

    @Test
    void registerShouldRejectShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"shortpw\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginShouldRejectWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"loginuser\",\"password\":\"password123\"}"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"loginuser\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatHistoryShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/chat/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userTokenShouldAccessHistoryButNotAdmin() throws Exception {
        String token = registerAndGetToken("historyuser");

        // 普通用户可访问自己的历史
        mockMvc.perform(get("/api/chat/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 普通用户不可访问管理端
        mockMvc.perform(get("/api/admin/risk-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminShouldAccessRiskEvents() throws Exception {
        // 管理员账号由 DataInitializer 引导（test profile 中指定了固定密码）
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-admin\",\"password\":\"test-admin-pass\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        String adminToken = body.get("token").asText();
        assertThat(adminToken).isNotBlank();
        assertThat(body.get("role").asText()).isEqualTo("ROLE_ADMIN");

        mockMvc.perform(get("/api/admin/risk-events")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void invalidTokenShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/chat/history")
                        .header("Authorization", "Bearer forged-token-value"))
                .andExpect(status().isUnauthorized());
    }

    private String registerAndGetToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }
}

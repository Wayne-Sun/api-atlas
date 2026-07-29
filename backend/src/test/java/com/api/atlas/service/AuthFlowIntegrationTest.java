package com.api.atlas.service;

import com.api.atlas.model.TokenSession;
import com.api.atlas.model.User;
import com.api.atlas.model.UserCreateDTO;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the complete authentication flow:
 * user creation → login → protected resource access → logout → token revocation.
 *
 * Uses real UserService + UserMapper backed by H2, but mocks Redis
 * (RedisTokenService + RedisTemplate) to avoid needing a real Redis server.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthFlowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserService userService;

    @MockitoBean
    private RedisTokenService redisTokenService;

    @MockitoBean(name = "redisTokenTemplate")
    private RedisTemplate<String, TokenSession> redisTokenTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        // Default: valid tokens pass the revocation check
        when(redisTokenService.exists(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("完整认证流程: 登录 → 访问受保护资源 → 登出 → token 失效")
    void login_AccessProtectedResource_Logout_TokenRevoked() throws Exception {
        // 1. Create a test user with known credentials via the real UserService
        UserCreateDTO createDTO = new UserCreateDTO();
        createDTO.setUsername("auth-flow-user-" + System.nanoTime());
        createDTO.setPassword("testPassword123");
        createDTO.setDisplayName("Auth Flow Test User");
        createDTO.setRole("USER");
        User createdUser = userService.create(createDTO);

        // 2. Login → expect 200 and an access token
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + createdUser.getUsername()
                                + "\",\"password\":\"testPassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.username").value(createdUser.getUsername()))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.data.accessToken");

        // 3. Access a protected resource with the valid token → 200
        mockMvc.perform(get("/api/datasources")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 4. Logout → 200 (token revoked from Redis)
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 5. Access the same protected resource with the revoked token → 401
        when(redisTokenService.exists(anyString())).thenReturn(false);
        mockMvc.perform(get("/api/datasources")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登录 - 密码错误返回 401")
    void login_InvalidPassword_Returns401() throws Exception {
        // 1. Create test user with known password
        UserCreateDTO createDTO = new UserCreateDTO();
        createDTO.setUsername("auth-flow-bad-pw-" + System.nanoTime());
        createDTO.setPassword("correctPassword");
        createDTO.setDisplayName("Bad Password Test");
        createDTO.setRole("USER");
        userService.create(createDTO);

        // 2. Login with wrong password → 401
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + createDTO.getUsername()
                                + "\",\"password\":\"wrongPassword\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未携带 token 访问受保护资源返回 401")
    void accessWithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/datasources"))
                .andExpect(status().isUnauthorized());
    }
}

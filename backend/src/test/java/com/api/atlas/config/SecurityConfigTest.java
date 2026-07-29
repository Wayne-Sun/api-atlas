package com.api.atlas.config;

import com.api.atlas.model.TokenSession;
import com.api.atlas.service.JwtTokenService;
import com.api.atlas.service.RedisTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenService jwtTokenService;

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
    }

    @Test
    void unauthenticatedRequest_Returns401() throws Exception {
        mockMvc.perform(get("/api/datasources"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginEndpoint_AccessibleWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk()) // AuthController exists — validation error returns 200 with R(code=400)
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void optionsRequest_AccessibleWithoutAuth() throws Exception {
        mockMvc.perform(options("/api/datasources")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk()); // CORS preflight passes through
    }

    @Test
    void requestWithValidToken_Passes() throws Exception {
        String token = jwtTokenService.generateAccessToken("testuser", "ADMIN");
        when(redisTokenService.exists(anyString())).thenReturn(true);

        mockMvc.perform(get("/api/datasources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void requestWithRevokedToken_Returns401() throws Exception {
        String token = jwtTokenService.generateAccessToken("testuser", "ADMIN");
        when(redisTokenService.exists(anyString())).thenReturn(false);

        mockMvc.perform(get("/api/datasources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}

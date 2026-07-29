package com.api.atlas.controller;

import com.api.atlas.model.TokenSession;
import com.api.atlas.model.User;
import com.api.atlas.service.JwtTokenService;
import com.api.atlas.service.RedisTokenService;
import com.api.atlas.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RedisTokenService redisTokenService;

    @MockitoBean(name = "redisTokenTemplate")
    private RedisTemplate<String, TokenSession> redisTokenTemplate;

    private MockMvc mockMvc;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        passwordEncoder = new BCryptPasswordEncoder();
    }

    @Test
    void login_ValidCredentials_ReturnsToken() throws Exception {
        String encodedPassword = passwordEncoder.encode("password123");
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setDisplayName("Admin User");
        user.setRole("ADMIN");
        user.setStatus("ENABLED");
        user.setPassword(encodedPassword);

        when(userService.getUserByUsername("admin")).thenReturn(user);
        // JwtTokenService is NOT mocked — real tokens are generated and decoded

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

        // Verify token session was saved to Redis
        verify(redisTokenService).saveToken(anyString(), any(TokenSession.class), anyLong());
    }

    @Test
    void login_InvalidPassword_Returns401() throws Exception {
        String encodedPassword = passwordEncoder.encode("correctpassword");
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword(encodedPassword);
        user.setStatus("ENABLED");

        when(userService.getUserByUsername("admin")).thenReturn(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_DisabledAccount_Returns401() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("disableduser");
        user.setStatus("DISABLED");
        user.setPassword(passwordEncoder.encode("password123"));

        when(userService.getUserByUsername("disableduser")).thenReturn(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"disableduser\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account disabled"));
    }

    @Test
    void login_NonExistentUser_Returns401() throws Exception {
        when(userService.getUserByUsername("nonexistent")).thenReturn(null);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nonexistent\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_ValidToken_Returns200() throws Exception {
        String token = jwtTokenService.generateAccessToken("testuser", "ADMIN");
        when(redisTokenService.exists(anyString())).thenReturn(true);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(redisTokenService).removeToken(anyString());
    }

    @Test
    void me_Authenticated_ReturnsUserInfo() throws Exception {
        String token = jwtTokenService.generateAccessToken("testuser", "ADMIN");

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setDisplayName("Test User");
        user.setRole("ADMIN");

        when(userService.getUserByUsername("testuser")).thenReturn(user);
        when(redisTokenService.exists(anyString())).thenReturn(true);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.displayName").value("Test User"));
    }

    @Test
    void me_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}

package com.api.atlas.controller;

import com.api.atlas.model.TokenSession;
import com.api.atlas.model.User;
import com.api.atlas.model.UserCreateDTO;
import com.api.atlas.service.RedisTokenService;
import com.api.atlas.service.UserService;
import com.github.pagehelper.PageInfo;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class UserControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RedisTokenService redisTokenService;

    @MockitoBean(name = "redisTokenTemplate")
    private RedisTemplate<String, TokenSession> redisTokenTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void list_AsAdmin_Returns200() throws Exception {
        when(userService.list(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageInfo<>(List.of()));

        mockMvc.perform(get("/api/users")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void list_AsUser_Returns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_AsAdmin_Returns200() throws Exception {
        User created = new User();
        created.setId(1L);
        created.setUsername("newuser");
        created.setDisplayName("New User");
        created.setRole("USER");
        created.setStatus("ENABLED");

        when(userService.create(any(UserCreateDTO.class))).thenReturn(created);

        String body = "{\"username\":\"newuser\",\"password\":\"password123\",\"displayName\":\"New User\",\"role\":\"USER\"}";
        mockMvc.perform(post("/api/users")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void getById_AsAdmin_Returns200() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setDisplayName("Test User");
        user.setRole("USER");

        when(userService.getById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/users/1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void delete_AsAdmin_Returns200() throws Exception {
        mockMvc.perform(delete("/api/users/1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}

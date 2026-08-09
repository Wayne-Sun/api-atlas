package com.api.atlas.controller;

import com.api.atlas.model.ApiInterface;
import com.api.atlas.service.ApiInterfaceService;
import com.api.atlas.service.executor.QueryResult;
import com.github.pagehelper.PageInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@SpringBootTest
@ActiveProfiles("test")
class InterfaceControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private ApiInterfaceService apiInterfaceService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // ── USER role: mutations/test → 403 ────────────────────────────

    @Test
    void create_UserRole_Returns403() throws Exception {
        mockMvc.perform(post("/api/interfaces")
                        .with(user("user").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"englishName\":\"test\",\"chineseName\":\"测试\",\"method\":\"POST\",\"dataSourceId\":1,\"queryType\":\"SQL\",\"queryContent\":\"SELECT 1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void update_UserRole_Returns403() throws Exception {
        mockMvc.perform(put("/api/interfaces/1")
                        .with(user("user").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"englishName\":\"updated\",\"chineseName\":\"更新\",\"method\":\"POST\",\"dataSourceId\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void delete_UserRole_Returns403() throws Exception {
        mockMvc.perform(delete("/api/interfaces/1")
                        .with(user("user").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void test_UserRole_Returns403() throws Exception {
        mockMvc.perform(post("/api/interfaces/1/test")
                        .with(user("user").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void updateStatus_UserRole_Returns403() throws Exception {
        mockMvc.perform(patch("/api/interfaces/1/status")
                        .with(user("user").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ONLINE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    // ── USER role: reads → 200 ─────────────────────────────────────

    @Test
    void list_UserRole_Returns200() throws Exception {
        when(apiInterfaceService.list(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageInfo<>(List.of()));

        mockMvc.perform(get("/api/interfaces")
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getById_UserRole_Returns200() throws Exception {
        ApiInterface iface = new ApiInterface();
        iface.setId(1L);
        iface.setEnglishName("test");
        when(apiInterfaceService.getById(1L)).thenReturn(iface);

        mockMvc.perform(get("/api/interfaces/1")
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.englishName").value("test"));
    }

    // ── ADMIN role: mutations/test → 200 ───────────────────────────

    @Test
    void create_AdminRole_Returns201() throws Exception {
        ApiInterface iface = new ApiInterface();
        iface.setId(1L);
        iface.setEnglishName("test");
        when(apiInterfaceService.create(any())).thenReturn(iface);

        mockMvc.perform(post("/api/interfaces")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"englishName\":\"test\",\"chineseName\":\"测试\",\"method\":\"POST\",\"dataSourceId\":1,\"queryType\":\"SQL\",\"queryContent\":\"SELECT 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    void update_AdminRole_Returns200() throws Exception {
        ApiInterface iface = new ApiInterface();
        iface.setId(1L);
        iface.setEnglishName("updated");
        when(apiInterfaceService.update(eq(1L), any())).thenReturn(iface);

        mockMvc.perform(put("/api/interfaces/1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"englishName\":\"updated\",\"chineseName\":\"更新\",\"method\":\"POST\",\"dataSourceId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void delete_AdminRole_Returns204() throws Exception {
        doNothing().when(apiInterfaceService).delete(1L);

        mockMvc.perform(delete("/api/interfaces/1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void test_AdminRole_Returns200() throws Exception {
        QueryResult queryResult = new QueryResult();
        when(apiInterfaceService.testInterface(eq(1L), anyMap(), anyInt(), anyInt()))
                .thenReturn(queryResult);

        mockMvc.perform(post("/api/interfaces/1/test")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void test_ExecutorFailure_ReturnsGenericMessageWithoutRawSql() throws Exception {
        // The executor throws the neutralized IAE — only the datasource prefix
        // may surface in the body, never the raw SQL text or a stack trace.
        when(apiInterfaceService.testInterface(eq(1L), anyMap(), anyInt(), anyInt()))
                .thenThrow(new IllegalArgumentException("SQL execution failed for datasource 1"));

        mockMvc.perform(post("/api/interfaces/1/test")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("failed for datasource")))
                .andExpect(content().string(not(containsString("SELECT"))))
                .andExpect(content().string(not(containsString("at com.api.atlas"))));
    }

    @Test
    void updateStatus_AdminRole_Returns200() throws Exception {
        doNothing().when(apiInterfaceService).updateStatus(eq(1L), any());

        mockMvc.perform(patch("/api/interfaces/1/status")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ONLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}

package com.api.atlas.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.ElasticsearchClusterClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.transport.ElasticsearchTransport;
import com.api.atlas.config.DataSourceFactory;
import com.api.atlas.config.DataSourceFactoryRegistry;
import com.api.atlas.model.DataSource;
import com.api.atlas.service.DataSourceService;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
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

import java.io.IOException;
import java.sql.Connection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
class DataSourceControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private DataSourceService dataSourceService;

    @MockitoBean
    private DataSourceFactoryRegistry factoryRegistry;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void testConnection_MongoDB_ReturnsConnectedTrue() throws Exception {
        MongoClient mongoClient = mock(MongoClient.class);
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        when(mongoClient.getDatabase(anyString())).thenReturn(mongoDatabase);
        when(mongoDatabase.runCommand(any(Document.class))).thenReturn(new Document("ok", 1));

        DataSourceFactory<MongoClient> mongoFactory = factory();
        when(mongoFactory.createClient(anyString(), anyInt(), any(), any(), any(), any()))
                .thenReturn(mongoClient);
        when(factoryRegistry.<MongoClient>getFactory("MongoDB")).thenReturn(mongoFactory);

        mockMvc.perform(post("/api/datasources/test-connection")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MongoDB\",\"host\":\"8.8.8.8\",\"port\":27017}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true));
    }

    @Test
    void testConnection_MongoDB_FactoryThrowsIOException_ReturnsConnectedFalseWithGenericError() throws Exception {
        DataSourceFactory<MongoClient> mongoFactory = factory();
        when(mongoFactory.createClient(anyString(), anyInt(), any(), any(), any(), any()))
                .thenThrow(new IOException("connection refused"));
        when(factoryRegistry.<MongoClient>getFactory("MongoDB")).thenReturn(mongoFactory);

        mockMvc.perform(post("/api/datasources/test-connection")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MongoDB\",\"host\":\"8.8.8.8\",\"port\":27017}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false))
                .andExpect(jsonPath("$.data.error").value("Connection failed"))
                // The generic message must not leak the underlying exception detail
                .andExpect(content().string(not(containsString("connection refused"))))
                .andExpect(content().string(not(containsString("IOException"))))
                .andExpect(content().string(not(containsString("at com.api.atlas"))));
    }

    @Test
    void testConnection_Elasticsearch_ReturnsConnectedTrue() throws Exception {
        ElasticsearchClient esClient = mock(ElasticsearchClient.class);
        ElasticsearchClusterClient clusterClient = mock(ElasticsearchClusterClient.class);
        ElasticsearchTransport transport = mock(ElasticsearchTransport.class);
        when(esClient.cluster()).thenReturn(clusterClient);
        when(clusterClient.health()).thenReturn(mock(HealthResponse.class));
        // CRITICAL: bare mocks return null from _transport() -> NPE in finally -> connected=false
        when(esClient._transport()).thenReturn(transport);

        DataSourceFactory<ElasticsearchClient> esFactory = factory();
        when(esFactory.createClient(anyString(), anyInt(), any(), any(), any(), any()))
                .thenReturn(esClient);
        when(factoryRegistry.<ElasticsearchClient>getFactory("Elasticsearch")).thenReturn(esFactory);

        mockMvc.perform(post("/api/datasources/test-connection")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"Elasticsearch\",\"host\":\"8.8.8.8\",\"port\":9200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true));
    }

    @Test
    void testConnection_JdbcMysql_ReturnsConnectedTrue() throws Exception {
        javax.sql.DataSource ds = mock(javax.sql.DataSource.class);
        when(ds.getConnection()).thenReturn(mock(Connection.class));

        DataSourceFactory<javax.sql.DataSource> jdbcFactory = factory();
        when(jdbcFactory.createClient(anyString(), anyInt(), any(), any(), any(), any()))
                .thenReturn(ds);
        when(factoryRegistry.<javax.sql.DataSource>getFactory("MySQL")).thenReturn(jdbcFactory);

        mockMvc.perform(post("/api/datasources/test-connection")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MySQL\",\"host\":\"8.8.8.8\",\"port\":3306,\"databaseName\":\"api_atlas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true));
    }

    @Test
    void testConnection_Doris_ReturnsConnectedTrue() throws Exception {
        javax.sql.DataSource ds = mock(javax.sql.DataSource.class);
        when(ds.getConnection()).thenReturn(mock(Connection.class));

        DataSourceFactory<javax.sql.DataSource> dorisFactory = factory();
        when(dorisFactory.createClient(anyString(), anyInt(), any(), any(), any(), any()))
                .thenReturn(ds);
        when(factoryRegistry.<javax.sql.DataSource>getFactory("Doris")).thenReturn(dorisFactory);

        mockMvc.perform(post("/api/datasources/test-connection")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"Doris\",\"host\":\"8.8.8.8\",\"port\":9030,\"databaseName\":\"api_atlas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true));
    }

    @Test
    void testConnection_BlockedHost_ReturnsConnectedFalseHostNotAllowed() throws Exception {
        mockMvc.perform(post("/api/datasources/test-connection")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MySQL\",\"host\":\"127.0.0.1\",\"port\":3306}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false))
                .andExpect(jsonPath("$.data.error").value("Host not allowed"));

        verify(factoryRegistry, never()).getFactory(anyString());
    }

    @Test
    void testConnection_PublicHost_ProceedsToFactory() throws Exception {
        javax.sql.DataSource ds = mock(javax.sql.DataSource.class);
        when(ds.getConnection()).thenReturn(mock(Connection.class));

        DataSourceFactory<javax.sql.DataSource> jdbcFactory = factory();
        when(jdbcFactory.createClient(anyString(), anyInt(), any(), any(), any(), any()))
                .thenReturn(ds);
        when(factoryRegistry.<javax.sql.DataSource>getFactory("MySQL")).thenReturn(jdbcFactory);

        mockMvc.perform(post("/api/datasources/test-connection")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MySQL\",\"host\":\"8.8.8.8\",\"port\":53}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true));

        verify(jdbcFactory).createClient("8.8.8.8", 53, "", null, null, null);
    }

    @Test
    void create_UserRole_Returns403() throws Exception {
        mockMvc.perform(post("/api/datasources")
                        .with(user("user").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"type\":\"MySQL\",\"host\":\"localhost\",\"port\":3306,\"username\":\"root\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void update_UserRole_Returns403() throws Exception {
        mockMvc.perform(put("/api/datasources/1")
                        .with(user("user").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"updated\",\"type\":\"MySQL\",\"host\":\"localhost\",\"port\":3306}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void delete_UserRole_Returns403() throws Exception {
        mockMvc.perform(delete("/api/datasources/1")
                        .with(user("user").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void updateStatus_UserRole_Returns403() throws Exception {
        mockMvc.perform(patch("/api/datasources/1/status")
                        .with(user("user").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENABLED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void testConnection_UserRole_Returns403() throws Exception {
        mockMvc.perform(post("/api/datasources/test-connection")
                        .with(user("user").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MySQL\",\"host\":\"localhost\",\"port\":3306}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void list_UserRole_Returns200() throws Exception {
        when(dataSourceService.list(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new com.github.pagehelper.PageInfo<>(List.of()));

        mockMvc.perform(get("/api/datasources")
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getById_UserRole_Returns200() throws Exception {
        DataSource ds = new DataSource();
        ds.setId(1L);
        ds.setName("test");
        when(dataSourceService.getById(1L)).thenReturn(ds);

        mockMvc.perform(get("/api/datasources/1")
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("test"));
    }

    @Test
    void create_AdminRole_Returns201() throws Exception {
        DataSource ds = new DataSource();
        ds.setId(1L);
        ds.setName("test");
        when(dataSourceService.create(any())).thenReturn(ds);

        mockMvc.perform(post("/api/datasources")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"type\":\"MySQL\",\"host\":\"localhost\",\"port\":3306,\"username\":\"root\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));
    }

    @Test
    void update_AdminRole_Returns200() throws Exception {
        DataSource ds = new DataSource();
        ds.setId(1L);
        ds.setName("updated");
        when(dataSourceService.update(eq(1L), any())).thenReturn(ds);

        mockMvc.perform(put("/api/datasources/1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"updated\",\"type\":\"MySQL\",\"host\":\"localhost\",\"port\":3306,\"username\":\"root\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void delete_AdminRole_Returns204() throws Exception {
        mockMvc.perform(delete("/api/datasources/1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateStatus_AdminRole_Returns200() throws Exception {
        mockMvc.perform(patch("/api/datasources/1/status")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @SuppressWarnings("unchecked")
    private static <T> DataSourceFactory<T> factory() {
        return mock(DataSourceFactory.class);
    }
}

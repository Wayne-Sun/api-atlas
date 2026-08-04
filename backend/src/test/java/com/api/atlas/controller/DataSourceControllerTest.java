package com.api.atlas.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.ElasticsearchClusterClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.transport.ElasticsearchTransport;
import com.api.atlas.config.DataSourceFactory;
import com.api.atlas.config.DataSourceFactoryRegistry;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                        .content("{\"type\":\"MongoDB\",\"host\":\"localhost\",\"port\":27017}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true));
    }

    @Test
    void testConnection_MongoDB_FactoryThrowsIOException_ReturnsConnectedFalseWithError() throws Exception {
        DataSourceFactory<MongoClient> mongoFactory = factory();
        when(mongoFactory.createClient(anyString(), anyInt(), any(), any(), any(), any()))
                .thenThrow(new IOException("connection refused"));
        when(factoryRegistry.<MongoClient>getFactory("MongoDB")).thenReturn(mongoFactory);

        mockMvc.perform(post("/api/datasources/test-connection")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MongoDB\",\"host\":\"localhost\",\"port\":27017}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false))
                .andExpect(jsonPath("$.data.error").exists())
                .andExpect(jsonPath("$.data.error").isNotEmpty());
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
                        .content("{\"type\":\"Elasticsearch\",\"host\":\"localhost\",\"port\":9200}"))
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
                        .content("{\"type\":\"MySQL\",\"host\":\"localhost\",\"port\":3306,\"databaseName\":\"api_atlas\"}"))
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
                        .content("{\"type\":\"Doris\",\"host\":\"localhost\",\"port\":9030,\"databaseName\":\"api_atlas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true));
    }

    @SuppressWarnings("unchecked")
    private static <T> DataSourceFactory<T> factory() {
        return mock(DataSourceFactory.class);
    }
}

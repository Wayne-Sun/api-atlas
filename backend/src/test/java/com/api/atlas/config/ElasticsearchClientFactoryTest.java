package com.api.atlas.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link ElasticsearchClientFactory}.
 * No Spring context, no network — RestClient/RestClientTransport construction is lazy;
 * only host validation and object construction are asserted.
 */
@ExtendWith(MockitoExtension.class)
class ElasticsearchClientFactoryTest {

    private ElasticsearchClientFactory factory;

    @Mock
    private HostSecurityValidator hostSecurityValidator;

    @BeforeEach
    void setUp() {
        factory = new ElasticsearchClientFactory(hostSecurityValidator, "http");
    }

    @Test
    void createClient_BlockedHost_ThrowsIllegalArgumentException() {
        when(hostSecurityValidator.isBlocked("127.0.0.1")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                factory.createClient("127.0.0.1", 9200, null, null, null, null));

        assertTrue(ex.getMessage().contains("Host not allowed"), "expected host rejection but was: " + ex.getMessage());
        verify(hostSecurityValidator).isBlocked("127.0.0.1");
    }

    @Test
    void createClient_PublicHost_ReturnsElasticsearchClient() throws Exception {
        ElasticsearchClient client = factory.createClient("8.8.8.8", 9200, null, null, null, null);

        assertNotNull(client);
        factory.destroyClient(client);
    }

    @Test
    void createClient_PublicHostWithApiKey_ReturnsElasticsearchClient() throws Exception {
        ElasticsearchClient client = factory.createClient("8.8.8.8", 9200, null, "user", "pass", "api-key-123");

        assertNotNull(client);
        factory.destroyClient(client);
    }

    @Test
    void getType_ReturnsElasticsearch() {
        assertEquals("Elasticsearch", factory.getType());
    }
}

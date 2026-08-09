package com.api.atlas.config;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoClientFactoryTest {

    private MongoClientFactory factory;

    @Mock
    private MongoClient mockClient;

    @Mock
    private HostSecurityValidator hostSecurityValidator;

    @BeforeEach
    void setUp() {
        // @Mock validator defaults to false (not blocked) — existing localhost fixtures stay valid.
        factory = new MongoClientFactory(hostSecurityValidator, 5000, 5000, 60000);
    }

    @Test
    void buildConnectionString_WithCredentials_ReturnsParsedUri() {
        ConnectionString cs = factory.buildConnectionString("localhost", 27017, "user", "pass");

        assertTrue(cs.getHosts().contains("localhost:27017"), "hosts should contain localhost:27017");
        assertEquals("user", cs.getCredential().getUserName());
        assertEquals("admin", cs.getCredential().getSource());
        assertTrue(cs.getConnectionString().contains("connectTimeoutMS=5000"));
        assertTrue(cs.getConnectionString().contains("serverSelectionTimeoutMS=5000"));
        assertTrue(cs.getConnectionString().contains("socketTimeoutMS=60000"));
    }

    @Test
    void buildConnectionString_WithFullSrvHost_PassesThroughWithoutException() {
        ConnectionString cs = assertDoesNotThrow(() ->
                factory.buildConnectionString("mongodb+srv://cluster0.x.mongodb.net", 27017, null, null));

        assertNotNull(cs);
        assertTrue(cs.getConnectionString().startsWith("mongodb+srv://cluster0.x.mongodb.net"));
    }

    @Test
    void buildConnectionString_WithoutCredentials_ReturnsNoUserinfo() {
        ConnectionString cs = factory.buildConnectionString("localhost", 27017, null, null);

        assertNull(cs.getCredential());
    }

    @Test
    void buildConnectionString_WithSpecialCharPassword_ReturnsRoundTrippedPassword() {
        String password = "p@ss:w/rd";
        ConnectionString cs = factory.buildConnectionString("localhost", 27017, "user", password);

        ConnectionString reparsed = assertDoesNotThrow(() -> new ConnectionString(cs.toString()));
        assertArrayEquals(password.toCharArray(), reparsed.getCredential().getPassword());
    }

    @Test
    void buildConnectionString_FullUriWithEmbeddedCredentials_ThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                factory.buildConnectionString("mongodb://user:pass@127.0.0.1:27017", 27017, null, null));

        assertTrue(ex.getMessage().contains("Credentials must not be embedded in the connection string"),
                "expected embedded-credentials rejection but was: " + ex.getMessage());
    }

    @Test
    void buildConnectionString_FullUriWithBlockedHost_ThrowsIllegalArgumentException() {
        when(hostSecurityValidator.isBlocked("127.0.0.1")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                factory.buildConnectionString("mongodb://127.0.0.1:27017", 27017, null, null));

        assertTrue(ex.getMessage().contains("Host not allowed"), "expected host rejection but was: " + ex.getMessage());
        verify(hostSecurityValidator).isBlocked("127.0.0.1");
    }

    @Test
    void buildConnectionString_FullUriWithPublicHost_ReturnsParsedConnectionString() {
        ConnectionString cs = assertDoesNotThrow(() ->
                factory.buildConnectionString("mongodb://8.8.8.8:27017", 27017, null, null));

        assertTrue(cs.getHosts().contains("8.8.8.8:27017"), "hosts should contain 8.8.8.8:27017 but was: " + cs.getHosts());
        assertNull(cs.getCredential());
    }

    @Test
    void buildConnectionString_SrvUriWithPublicHost_ReturnsParsedConnectionString() {
        ConnectionString cs = assertDoesNotThrow(() ->
                factory.buildConnectionString("mongodb+srv://db.example.com", 27017, null, null));

        assertTrue(cs.isSrvProtocol(), "expected srv protocol for mongodb+srv URI");
        assertTrue(cs.getHosts().contains("db.example.com"), "hosts should contain db.example.com but was: " + cs.getHosts());
        verify(hostSecurityValidator).isBlocked("db.example.com");
    }

    @Test
    void buildConnectionString_PlainHostBlocked_ThrowsIllegalArgumentException() {
        when(hostSecurityValidator.isBlocked("127.0.0.1")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                factory.buildConnectionString("127.0.0.1", 27017, null, null));

        assertTrue(ex.getMessage().contains("Host not allowed"), "expected host rejection but was: " + ex.getMessage());
    }

    @Test
    void createClient_PublicHost_ReturnsMongoClient() {
        MongoClient client = factory.createClient("8.8.8.8", 27017, "testdb", null, null, null);

        assertNotNull(client);
        factory.destroyClient(client);
    }

    @Test
    void destroyClient_WithClient_ClosesItOnce() {
        factory.destroyClient(mockClient);

        verify(mockClient).close();
    }

    @Test
    void destroyClient_WithNull_DoesNotThrow() {
        assertDoesNotThrow(() -> factory.destroyClient(null));
    }

    @Test
    void getType_ReturnsMongoDB() {
        assertEquals("MongoDB", factory.getType());
    }
}

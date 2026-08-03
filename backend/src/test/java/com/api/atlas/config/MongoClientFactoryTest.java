package com.api.atlas.config;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MongoClientFactoryTest {

    private MongoClientFactory factory;

    @Mock
    private MongoClient mockClient;

    @BeforeEach
    void setUp() {
        factory = new MongoClientFactory();
        ReflectionTestUtils.setField(factory, "connectTimeoutMs", 5000);
        ReflectionTestUtils.setField(factory, "serverSelectionTimeoutMs", 5000);
        ReflectionTestUtils.setField(factory, "socketTimeoutMs", 60000);
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

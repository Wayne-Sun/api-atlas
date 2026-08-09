package com.api.atlas.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link DatabaseClientFactory}.
 * No Spring context, no real DB connections — HikariDataSource constructor does not connect;
 * only the JDBC URL string is asserted.
 */
class DatabaseClientFactoryTest {

    private static final int MAX_POOL_SIZE = 5;
    private static final int MIN_IDLE = 1;
    private static final int MAX_LIFETIME = 1800000;
    private static final int KEEPALIVE = 300000;

    private DatabaseClientFactory newFactory(String databaseType) {
        // Permissive validator (allow-private-hosts=true) keeps the existing
        // "localhost" URL-construction fixtures valid — host blocking is tested
        // separately with a strict validator below.
        return new DatabaseClientFactory(databaseType, MAX_POOL_SIZE, MIN_IDLE, MAX_LIFETIME, KEEPALIVE,
                new HostSecurityValidator(true));
    }

    private String jdbcUrlOf(String databaseType) {
        DatabaseClientFactory factory = newFactory(databaseType);
        HikariDataSource ds = assertInstanceOf(HikariDataSource.class,
                assertDoesNotThrow(() -> factory.createClient("localhost", 3306, "doris_db", "user", "pass", null)));
        return ds.getJdbcUrl();
    }

    @Test
    void createClient_DorisType_ReturnsHikariDataSourceWithAllRequiredUrlParams() {
        String jdbcUrl = jdbcUrlOf("Doris");

        assertTrue(jdbcUrl.startsWith("jdbc:mysql://"), "Doris URL must start with jdbc:mysql:// but was: " + jdbcUrl);
        // Per-parameter contains assertions (not full-string equality — resilient to param order).
        assertTrue(jdbcUrl.contains("useServerPrepStmts=false"), "missing useServerPrepStmts=false in: " + jdbcUrl);
        assertTrue(jdbcUrl.contains("allowPublicKeyRetrieval=true"), "missing allowPublicKeyRetrieval=true in: " + jdbcUrl);
        assertTrue(jdbcUrl.contains("zeroDateTimeBehavior=convertToNull"), "missing zeroDateTimeBehavior=convertToNull in: " + jdbcUrl);
        assertTrue(jdbcUrl.contains("useSSL=false"), "missing useSSL=false in: " + jdbcUrl);
        assertTrue(jdbcUrl.contains("useTimezone=true"), "missing useTimezone=true in: " + jdbcUrl);
        assertTrue(jdbcUrl.contains("serverTimezone=Asia/Shanghai"), "missing serverTimezone=Asia/Shanghai in: " + jdbcUrl);
        assertTrue(jdbcUrl.contains("characterEncoding=utf-8"), "missing characterEncoding=utf-8 in: " + jdbcUrl);
        assertTrue(jdbcUrl.contains("localhost:3306/doris_db"), "missing host/port/db in: " + jdbcUrl);
    }

    @Test
    void createClient_PostgreSQLType_DoesNotContainDorisOnlyParam() {
        String jdbcUrl = jdbcUrlOf("PostgreSQL");

        assertTrue(jdbcUrl.startsWith("jdbc:postgresql://"), "PostgreSQL URL must start with jdbc:postgresql:// but was: " + jdbcUrl);
        assertFalse(jdbcUrl.contains("useServerPrepStmts=false"), "PostgreSQL URL must not contain useServerPrepStmts=false: " + jdbcUrl);
    }

    @Test
    void createClient_MySQLType_DoesNotContainDorisOnlyParam() {
        String jdbcUrl = jdbcUrlOf("MySQL");

        assertTrue(jdbcUrl.startsWith("jdbc:mysql://"), "MySQL URL must start with jdbc:mysql:// but was: " + jdbcUrl);
        assertFalse(jdbcUrl.contains("useServerPrepStmts=false"), "MySQL URL must not contain useServerPrepStmts=false: " + jdbcUrl);
    }

    @Test
    void createClient_PrivateHost_ThrowsIllegalArgumentException() {
        DatabaseClientFactory factory = new DatabaseClientFactory("MySQL", MAX_POOL_SIZE, MIN_IDLE, MAX_LIFETIME, KEEPALIVE,
                new HostSecurityValidator(false));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                factory.createClient("192.168.0.5", 3306, "db", "user", "pass", null));

        assertTrue(ex.getMessage().contains("Host not allowed"), "expected host rejection but was: " + ex.getMessage());
    }

    @Test
    void createClient_PublicHost_ReturnsHikariDataSource() {
        DatabaseClientFactory factory = new DatabaseClientFactory("MySQL", MAX_POOL_SIZE, MIN_IDLE, MAX_LIFETIME, KEEPALIVE,
                new HostSecurityValidator(false));

        HikariDataSource ds = assertInstanceOf(HikariDataSource.class,
                assertDoesNotThrow(() -> factory.createClient("8.8.8.8", 3306, "public_db", "user", "pass", null)));

        assertTrue(ds.getJdbcUrl().startsWith("jdbc:mysql://8.8.8.8:3306/public_db"),
                "unexpected JDBC URL: " + ds.getJdbcUrl());
        ds.close();
    }
}

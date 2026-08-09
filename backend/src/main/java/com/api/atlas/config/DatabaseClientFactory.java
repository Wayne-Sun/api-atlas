package com.api.atlas.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

public class DatabaseClientFactory implements DataSourceFactory<DataSource> {

    private int maximumPoolSize;
    private int minimumIdle;
    private int maximumLifetime;
    private int keepaliveTime;

    private final String databaseType;
    private final HostSecurityValidator hostSecurityValidator;

    public DatabaseClientFactory(String databaseType, int maximumPoolSize, int minimumIdle, int maximumLifetime, int keepaliveTime,
                                 HostSecurityValidator hostSecurityValidator) {
        this.databaseType = databaseType;
        this.maximumPoolSize = maximumPoolSize;
        this.minimumIdle = minimumIdle;
        this.maximumLifetime = maximumLifetime;
        this.keepaliveTime = keepaliveTime;
        this.hostSecurityValidator = hostSecurityValidator;
    }

    @Override
    public DataSource createClient(String host, int port, String databaseName, String username, String password, String apiKey) throws Exception {
        if (hostSecurityValidator.isBlocked(host)) {
            throw new IllegalArgumentException("Host not allowed");
        }
        String dbName = (databaseName != null && !databaseName.isEmpty()) ? databaseName : "";
        String jdbcUrl;
        if ("PostgreSQL".equals(databaseType)) {
            jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + (dbName.isEmpty() ? "public" : dbName);
        } else if ("Doris".equals(databaseType)) {
            // useServerPrepStmts=false is MANDATORY for mysql-connector-j >= 9.5.0 connecting to
            // Apache Doris (official issue #60634) — server-side prepared statements return empty data.
            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName
                    + "?useUnicode=true&characterEncoding=utf-8&useTimezone=true&serverTimezone=Asia/Shanghai"
                    + "&useSSL=false&allowPublicKeyRetrieval=true&zeroDateTimeBehavior=convertToNull"
                    + "&useServerPrepStmts=false";
        } else {
            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName
                    + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai";
        }

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(maximumPoolSize);
        ds.setMinimumIdle(minimumIdle);
        ds.setMaxLifetime(maximumLifetime);
        ds.setKeepaliveTime(keepaliveTime);
        ds.setConnectionTimeout(5000);
        ds.setIdleTimeout(300000);
        ds.setConnectionTestQuery("SELECT 1");
        return ds;
    }

    @Override
    public void destroyClient(DataSource client) {
        if (client instanceof HikariDataSource) {
            ((HikariDataSource) client).close();
        }
    }

    @Override
    public String getType() {
        return databaseType;
    }
}

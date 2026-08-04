package com.api.atlas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceFactoryConfig {

    @Value("${atlas.datasource.hikari.maximum-pool-size:5}")
    private int maximumPoolSize;

    @Value("${atlas.datasource.hikari.minimum-idle:1}")
    private int minimumIdle;

    @Value("${atlas.datasource.hikari.maximum-lifetime:1800000}")
    private int maximumLifetime;

    @Value("${atlas.datasource.hikari.keepalive-time:300000}")
    private int keepaliveTime;

    @Bean
    public DatabaseClientFactory mySqlClientFactory() {
        return new DatabaseClientFactory("MySQL", maximumPoolSize, minimumIdle, maximumLifetime, keepaliveTime);
    }

    @Bean
    public DatabaseClientFactory postgreSqlClientFactory() {
        return new DatabaseClientFactory("PostgreSQL", maximumPoolSize, minimumIdle, maximumLifetime, keepaliveTime);
    }

    @Bean
    public DatabaseClientFactory dorisClientFactory() {
        return new DatabaseClientFactory("Doris", maximumPoolSize, minimumIdle, maximumLifetime, keepaliveTime);
    }
}

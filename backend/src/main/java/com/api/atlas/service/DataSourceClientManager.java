package com.api.atlas.service;

import com.api.atlas.config.DataSourceFactoryRegistry;
import com.api.atlas.config.EncryptionUtil;
import com.api.atlas.mapper.DataSourceMapper;
import com.api.atlas.model.DataSource;
import com.api.atlas.service.executor.DatabaseQueryExecutor;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DataSourceClientManager {

    private final Map<String, javax.sql.DataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final Map<String, ElasticsearchClient> esClientMap = new ConcurrentHashMap<>();
    private final DataSourceFactoryRegistry factoryRegistry;
    private final DataSourceMapper dataSourceMapper;
    private final SecretKey secretKey;
    private final ConcurrentHashMap<String, Object> lockMap = new ConcurrentHashMap<>();
    private final GenericApplicationContext applicationContext;
    private final List<DataSourceEventPublisher> eventPublishers;
    private final DatabaseQueryExecutor databaseQueryExecutor;

    @Autowired
    public DataSourceClientManager(DataSourceFactoryRegistry factoryRegistry,
                                   DataSourceMapper dataSourceMapper,
                                   SecretKey secretKey,
                               GenericApplicationContext applicationContext,
                               @Lazy List<DataSourceEventPublisher> eventPublishers,
                               @Lazy DatabaseQueryExecutor databaseQueryExecutor) {
        this.factoryRegistry = factoryRegistry;
        this.dataSourceMapper = dataSourceMapper;
        this.secretKey = secretKey;
        this.applicationContext = applicationContext;
        this.eventPublishers = eventPublishers;
        this.databaseQueryExecutor = databaseQueryExecutor;
    }

    /**
     * Enable a datasource: decrypt password, create client, register as Spring bean.
     */
    public void enableDataSource(Long id) {
        DataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw new NoSuchElementException("DataSource not found: " + id);
        }

        String datasourceId = "datasource_" + id;

        Object lock = lockMap.computeIfAbsent(datasourceId, k -> new Object());
        synchronized (lock) {
            if (dataSourceMap.containsKey(datasourceId)) {
                return;
            }

            String plainPassword = decrypt(ds.getPassword());

            try {
                if ("Elasticsearch".equals(ds.getType())) {
                    ElasticsearchClient client = (ElasticsearchClient) factoryRegistry.getFactory("Elasticsearch")
                            .createClient(ds.getHost(), ds.getPort(), ds.getDatabaseName(),
                                    ds.getUsername(), plainPassword, ds.getApiKey());
                    esClientMap.put(datasourceId, client);
                    applicationContext.getBeanFactory().registerSingleton(datasourceId, client);
                } else {
                    javax.sql.DataSource client = (javax.sql.DataSource) factoryRegistry.getFactory(ds.getType())
                            .createClient(ds.getHost(), ds.getPort(),
                                    ds.getDatabaseName() != null ? ds.getDatabaseName() : "",
                                    ds.getUsername(), plainPassword, ds.getApiKey());
                    dataSourceMap.put(datasourceId, client);
                    applicationContext.getBeanFactory().registerSingleton(datasourceId, client);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to enable datasource: " + id, e);
            }
        }
    }

    /**
     * Disable a datasource: close connection, notify publishers, remove bean.
     */
    public void disableDataSource(Long id) {
        String datasourceId = "datasource_" + id;

        Object lock = lockMap.computeIfAbsent(datasourceId, k -> new Object());
        synchronized (lock) {
            databaseQueryExecutor.clearCache(id);

            for (DataSourceEventPublisher publisher : eventPublishers) {
                publisher.onDataSourceDisabled(id, datasourceId);
            }

            javax.sql.DataSource ds = dataSourceMap.remove(datasourceId);
            if (ds instanceof HikariDataSource) {
                ((HikariDataSource) ds).close();
            }

            ElasticsearchClient es = esClientMap.remove(datasourceId);
            if (es != null) {
                try {
                    es._transport().close();
                } catch (Exception ignored) {
                    // ignore close errors
                }
            }
        }
    }

    /**
     * Get DataSource by ID (for SQL query execution).
     */
    public javax.sql.DataSource getDataSource(Long id) {
        javax.sql.DataSource ds = dataSourceMap.get("datasource_" + id);
        if (ds == null) {
            throw new IllegalArgumentException("DataSource not enabled: " + id);
        }
        return ds;
    }

    /**
     * Get ElasticsearchClient by ID.
     */
    public ElasticsearchClient getEsClient(Long id) {
        ElasticsearchClient client = esClientMap.get("datasource_" + id);
        if (client == null) {
            throw new IllegalArgumentException("DataSource not enabled: " + id);
        }
        return client;
    }

    private String decrypt(String ciphertext) {
        return EncryptionUtil.decrypt(ciphertext, secretKey);
    }
}

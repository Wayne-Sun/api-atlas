package com.api.atlas.service;

import com.api.atlas.config.DataSourceFactory;
import com.api.atlas.config.DataSourceFactoryRegistry;
import com.api.atlas.config.EncryptionUtil;
import com.api.atlas.mapper.DataSourceMapper;
import com.api.atlas.model.DataSource;
import com.api.atlas.service.executor.DatabaseQueryExecutor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSourceClientManagerTest {

    @Mock
    private DataSourceFactoryRegistry factoryRegistry;

    @Mock
    private DataSourceMapper dataSourceMapper;

    @Mock
    private SecretKey secretKey;

    @Mock
    private GenericApplicationContext applicationContext;

    @Mock
    private ConfigurableListableBeanFactory beanFactory;

    @Mock
    private List<DataSourceEventPublisher> eventPublishers;

    @Mock
    private DatabaseQueryExecutor databaseQueryExecutor;

    @Mock
    private DataSourceFactory<MongoClient> mongoFactory;

    @Mock
    private MongoClient mongoClient;

    @Mock
    private MongoDatabase mongoDatabase;

    @InjectMocks
    private DataSourceClientManager manager;

    private MockedStatic<EncryptionUtil> encryptionUtil;

    @BeforeEach
    void setUp() {
        encryptionUtil = mockStatic(EncryptionUtil.class);
        encryptionUtil.when(() -> EncryptionUtil.decrypt(anyString(), any(SecretKey.class)))
            .thenReturn("plain-password");
    }

    @AfterEach
    void tearDown() {
        if (encryptionUtil != null) {
            encryptionUtil.close();
        }
    }

    @Test
    @DisplayName("启用 MongoDB - 创建客户端并注册 Spring bean")
    void enable_MongoDB_CreatesClientAndRegistersSingleton() throws Exception {
        DataSource ds = mongoDataSource("ENABLED", "testdb");
        when(dataSourceMapper.selectById(1L)).thenReturn(ds);
        when(factoryRegistry.<MongoClient>getFactory("MongoDB")).thenReturn(mongoFactory);
        when(mongoFactory.createClient(eq("localhost"), eq(27017), eq("testdb"), eq("user"), eq("plain-password"), isNull()))
            .thenReturn(mongoClient);
        when(applicationContext.getBeanFactory()).thenReturn(beanFactory);
        when(applicationContext.containsBean("datasource_1")).thenReturn(false);

        manager.enableDataSource(1L);

        verify(mongoFactory).createClient("localhost", 27017, "testdb", "user", "plain-password", null);
        verify(beanFactory).registerSingleton("datasource_1", mongoClient);
    }

    @Test
    @DisplayName("启用 MongoDB - 二次启用不重复创建客户端")
    void enable_MongoDB_SecondEnable_DoesNotCreateDuplicate() throws Exception {
        DataSource ds = mongoDataSource("ENABLED", "testdb");
        when(dataSourceMapper.selectById(1L)).thenReturn(ds);
        when(factoryRegistry.<MongoClient>getFactory("MongoDB")).thenReturn(mongoFactory);
        when(mongoFactory.createClient(anyString(), anyInt(), any(), any(), any(), nullable(String.class)))
            .thenReturn(mongoClient);
        when(applicationContext.getBeanFactory()).thenReturn(beanFactory);
        when(applicationContext.containsBean(anyString())).thenReturn(false);

        manager.enableDataSource(1L);
        manager.enableDataSource(1L);

        verify(mongoFactory, times(1)).createClient(anyString(), anyInt(), any(), any(), any(), nullable(String.class));
        verify(beanFactory, times(1)).registerSingleton("datasource_1", mongoClient);
    }

    @Test
    @DisplayName("禁用 MongoDB - 关闭客户端")
    void disable_MongoDB_ClosesClient() throws Exception {
        DataSource ds = mongoDataSource("ENABLED", "testdb");
        when(dataSourceMapper.selectById(1L)).thenReturn(ds);
        when(factoryRegistry.<MongoClient>getFactory("MongoDB")).thenReturn(mongoFactory);
        when(mongoFactory.createClient(anyString(), anyInt(), any(), any(), any(), nullable(String.class)))
            .thenReturn(mongoClient);
        when(applicationContext.getBeanFactory()).thenReturn(beanFactory);
        when(applicationContext.containsBean(anyString())).thenReturn(false);

        manager.enableDataSource(1L);
        when(eventPublishers.iterator()).thenReturn(Collections.emptyIterator());
        manager.disableDataSource(1L);

        verify(mongoClient).close();
    }

    @Test
    @DisplayName("获取 MongoClient - 数据源禁用时抛异常且不创建客户端")
    void getMongoClient_DisabledStatus_ThrowsIllegalArgumentException() throws Exception {
        DataSource ds = mongoDataSource("DISABLED", "testdb");
        when(dataSourceMapper.selectById(1L)).thenReturn(ds);

        assertThatThrownBy(() -> manager.getMongoClient(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not enabled");

        verify(mongoFactory, never()).createClient(anyString(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("获取 MongoClient - 懒加载只创建一次并缓存复用")
    void getMongoClient_LazyCreation_ReusesCachedClient() throws Exception {
        DataSource ds = mongoDataSource("ENABLED", "testdb");
        when(dataSourceMapper.selectById(1L)).thenReturn(ds);
        when(factoryRegistry.<MongoClient>getFactory("MongoDB")).thenReturn(mongoFactory);
        when(mongoFactory.createClient(anyString(), anyInt(), any(), any(), any(), nullable(String.class)))
            .thenReturn(mongoClient);
        when(applicationContext.getBeanFactory()).thenReturn(beanFactory);
        when(applicationContext.containsBean(anyString())).thenReturn(false);

        MongoClient first = manager.getMongoClient(1L);
        MongoClient second = manager.getMongoClient(1L);

        assertThat(first).isSameAs(mongoClient);
        assertThat(second).isSameAs(mongoClient);
        verify(mongoFactory, times(1)).createClient(anyString(), anyInt(), any(), any(), any(), nullable(String.class));
        verify(dataSourceMapper, times(1)).selectById(1L);
    }

    @Test
    @DisplayName("获取 MongoClient - 记录不存在时抛异常")
    void getMongoClient_RecordNotFound_ThrowsIllegalArgumentException() {
        when(dataSourceMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> manager.getMongoClient(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not enabled");
    }

    @Test
    @DisplayName("获取 MongoDatabase - 使用记录数据库名，为空时回退 admin")
    void getMongoDatabase_UsesRecordDbName_EmptyFallsBackToAdmin() throws Exception {
        DataSource ds = mongoDataSource("ENABLED", "testdb");
        when(dataSourceMapper.selectById(1L)).thenReturn(ds);
        when(factoryRegistry.<MongoClient>getFactory("MongoDB")).thenReturn(mongoFactory);
        when(mongoFactory.createClient(anyString(), anyInt(), any(), any(), any(), nullable(String.class)))
            .thenReturn(mongoClient);
        when(applicationContext.getBeanFactory()).thenReturn(beanFactory);
        when(applicationContext.containsBean(anyString())).thenReturn(false);
        when(mongoClient.getDatabase("testdb")).thenReturn(mongoDatabase);

        MongoDatabase db = manager.getMongoDatabase(1L);

        assertThat(db).isSameAs(mongoDatabase);
        verify(mongoClient).getDatabase("testdb");

        // empty databaseName → "admin"
        DataSource ds2 = mongoDataSource("ENABLED", "");
        ds2.setId(2L);
        when(dataSourceMapper.selectById(2L)).thenReturn(ds2);
        when(mongoClient.getDatabase("admin")).thenReturn(mongoDatabase);

        MongoDatabase db2 = manager.getMongoDatabase(2L);

        assertThat(db2).isSameAs(mongoDatabase);
        verify(mongoClient).getDatabase("admin");
        verify(mongoFactory, times(2)).createClient(anyString(), anyInt(), any(), any(), any(), nullable(String.class));
    }

    @Test
    @DisplayName("启停后懒重建 - containsBean 为 true 时跳过重复注册")
    void enableDisableLazyRebuild_BeanAlreadyRegistered_SkipsRegisterSingleton() throws Exception {
        DataSource ds = mongoDataSource("ENABLED", "testdb");
        when(dataSourceMapper.selectById(1L)).thenReturn(ds);
        when(factoryRegistry.<MongoClient>getFactory("MongoDB")).thenReturn(mongoFactory);
        when(mongoFactory.createClient(anyString(), anyInt(), any(), any(), any(), nullable(String.class)))
            .thenReturn(mongoClient);
        when(applicationContext.containsBean(anyString())).thenReturn(true);

        manager.enableDataSource(1L);
        when(eventPublishers.iterator()).thenReturn(Collections.emptyIterator());
        manager.disableDataSource(1L);
        manager.getMongoClient(1L);

        verify(beanFactory, never()).registerSingleton(anyString(), any());
        verify(mongoFactory, times(2)).createClient(anyString(), anyInt(), any(), any(), any(), nullable(String.class));
        verify(mongoClient).close();
    }

    private DataSource mongoDataSource(String status, String databaseName) {
        DataSource ds = new DataSource();
        ds.setId(1L);
        ds.setName("mongo-ds");
        ds.setType("MongoDB");
        ds.setHost("localhost");
        ds.setPort(27017);
        ds.setDatabaseName(databaseName);
        ds.setUsername("user");
        ds.setPassword("encrypted-password");
        ds.setStatus(status);
        return ds;
    }
}

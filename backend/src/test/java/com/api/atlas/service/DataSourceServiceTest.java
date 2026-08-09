package com.api.atlas.service;

import com.api.atlas.config.EncryptionUtil;
import com.api.atlas.mapper.DataSourceMapper;
import com.api.atlas.model.DataSource;
import com.api.atlas.model.DataSourceCreateDTO;
import com.api.atlas.model.DataSourceUpdateDTO;
import com.github.pagehelper.PageInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSourceServiceTest {

    @Mock
    private DataSourceMapper dataSourceMapper;

    @Mock
    private SecretKey secretKey;

    @InjectMocks
    private DataSourceService dataSourceService;

    private MockedStatic<EncryptionUtil> encryptionUtil;

    @BeforeEach
    void setUp() {
        encryptionUtil = mockStatic(EncryptionUtil.class);
        encryptionUtil.when(() -> EncryptionUtil.encrypt(anyString(), any(SecretKey.class)))
            .thenReturn("encrypted:test-password");
    }

    @AfterEach
    void tearDown() {
        if (encryptionUtil != null) {
            encryptionUtil.close();
        }
    }

    @Test
    @DisplayName("创建数据源 - 响应中密码/API Key 为空，但 Mapper 收到加密密文")
    void create_ReturnsNullCredentials() {
        DataSourceCreateDTO dto = new DataSourceCreateDTO();
        dto.setName("test-ds");
        dto.setType("MySQL");
        dto.setHost("localhost");
        dto.setPort(3306);
        dto.setDatabaseName("testdb");
        dto.setUsername("test-user");
        dto.setPassword("raw-password");
        dto.setApiKey("test-api-key");

        // Snapshot values the mapper receives — the returned entity is the SAME reference
        // that the service nulls after insert, so capture inside the answer.
        String[] persistedPassword = new String[1];
        String[] persistedApiKey = new String[1];
        when(dataSourceMapper.insert(any(DataSource.class))).thenAnswer(invocation -> {
            DataSource ds = invocation.getArgument(0);
            persistedPassword[0] = ds.getPassword();
            persistedApiKey[0] = ds.getApiKey();
            ds.setId(1L);
            return 1;
        });

        DataSource result = dataSourceService.create(dto);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("test-ds");
        assertThat(result.getType()).isEqualTo("MySQL");
        assertThat(result.getHost()).isEqualTo("localhost");
        assertThat(result.getPort()).isEqualTo(3306);
        assertThat(result.getDatabaseName()).isEqualTo("testdb");
        assertThat(result.getUsername()).isEqualTo("test-user");
        assertThat(result.getPassword()).isNull();
        assertThat(result.getApiKey()).isNull();
        assertThat(result.getStatus()).isEqualTo("ENABLED");

        assertThat(persistedPassword[0]).isEqualTo("encrypted:test-password");
        assertThat(persistedApiKey[0]).isEqualTo("test-api-key");

        // createdAt and updatedAt are now set by AuditInterceptor (MyBatis plugin)
        // — no longer set manually in the service layer

        encryptionUtil.verify(() -> EncryptionUtil.encrypt("raw-password", secretKey));
    }

    @Test
    @DisplayName("按 ID 查询 - 响应中密码和 API Key 均为空（不返回解密值）")
    void getById_ReturnsPasswordAndApiKeyNull() {
        DataSource ds = new DataSource();
        ds.setId(1L);
        ds.setName("test-ds");
        ds.setPassword("encrypted:cipher");
        ds.setApiKey("api-key-from-db");
        when(dataSourceMapper.selectById(1L)).thenReturn(ds);

        DataSource result = dataSourceService.getById(1L);

        assertThat(result).isSameAs(ds);
        assertThat(result.getName()).isEqualTo("test-ds");
        assertThat(result.getPassword()).isNull();
        assertThat(result.getApiKey()).isNull();
    }

    @Test
    @DisplayName("按 ID 查询 - 存在返回数据源")
    void getById_ExistingId_ReturnsDataSource() {
        DataSource ds = new DataSource();
        ds.setId(1L);
        ds.setName("test-ds");
        when(dataSourceMapper.selectById(1L)).thenReturn(ds);

        DataSource result = dataSourceService.getById(1L);

        assertThat(result).isSameAs(ds);
        assertThat(result.getName()).isEqualTo("test-ds");
    }

    @Test
    @DisplayName("按 ID 查询 - 不存在抛出 NoSuchElementException")
    void getById_NonExistingId_ThrowsNoSuchElementException() {
        when(dataSourceMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> dataSourceService.getById(9999L))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("9999");
    }

    @Test
    @DisplayName("查询列表 - 返回分页结果")
    void list_WithFilters_ReturnsPagedResults() {
        DataSource ds1 = new DataSource();
        ds1.setId(1L);
        ds1.setName("ds1");
        DataSource ds2 = new DataSource();
        ds2.setId(2L);
        ds2.setName("ds2");
        List<DataSource> mockList = List.of(ds1, ds2);

        when(dataSourceMapper.selectList(any(), any(), any())).thenReturn(mockList);

        PageInfo<DataSource> result = dataSourceService.list(null, null, null, 1, 10);

        assertThat(result.getList()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(2);
    }

    @Test
    @DisplayName("更新数据源 - 字段正确更新")
    void update_ValidDTO_UpdatesFields() {
        DataSource existing = new DataSource();
        existing.setId(1L);
        existing.setName("old-name");
        existing.setHost("old-host");

        DataSource updated = new DataSource();
        updated.setId(1L);
        updated.setName("new-name");
        updated.setHost("new-host");

        DataSourceUpdateDTO dto = new DataSourceUpdateDTO();
        dto.setName("new-name");
        dto.setHost("new-host");

        when(dataSourceMapper.selectById(1L)).thenReturn(existing, updated);
        when(dataSourceMapper.updateById(any(DataSource.class))).thenReturn(1);

        DataSource result = dataSourceService.update(1L, dto);

        assertThat(result.getName()).isEqualTo("new-name");
        assertThat(result.getHost()).isEqualTo("new-host");
    }

    @Test
    @DisplayName("更新数据源 - 不存在抛出 NoSuchElementException")
    void update_NonExistingId_ThrowsNoSuchElementException() {
        when(dataSourceMapper.selectById(9999L)).thenReturn(null);

        DataSourceUpdateDTO dto = new DataSourceUpdateDTO();
        dto.setName("test");

        assertThatThrownBy(() -> dataSourceService.update(9999L, dto))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("9999");
    }

    @Test
    @DisplayName("更新数据源 - 空白密码/API Key 表示不修改，保留原密文")
    void update_BlankPassword_KeepsExistingCiphertext() {
        DataSource existing = new DataSource();
        existing.setId(1L);
        existing.setName("test-ds");
        existing.setPassword("ciphertext-original");
        existing.setApiKey("original-api-key");

        DataSource afterDb = new DataSource();
        afterDb.setId(1L);
        afterDb.setName("test-ds");
        afterDb.setPassword("ciphertext-original");
        afterDb.setApiKey("original-api-key");

        DataSourceUpdateDTO dto = new DataSourceUpdateDTO();
        dto.setName("new-name");
        dto.setPassword("   ");
        dto.setApiKey(" ");

        when(dataSourceMapper.selectById(1L)).thenReturn(existing, afterDb);
        when(dataSourceMapper.updateById(any(DataSource.class))).thenReturn(1);

        dataSourceService.update(1L, dto);

        ArgumentCaptor<DataSource> captor = ArgumentCaptor.forClass(DataSource.class);
        verify(dataSourceMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("ciphertext-original");
        assertThat(captor.getValue().getApiKey()).isEqualTo("original-api-key");
    }

    @Test
    @DisplayName("更新数据源 - 响应中密码和 API Key 均为空")
    void update_ReturnsNullCredentials() {
        DataSource existing = new DataSource();
        existing.setId(1L);
        existing.setName("test-ds");
        existing.setPassword("ciphertext-original");

        DataSource afterDb = new DataSource();
        afterDb.setId(1L);
        afterDb.setName("updated-name");
        afterDb.setPassword("ciphertext-original");
        afterDb.setApiKey("api-key-in-db");

        DataSourceUpdateDTO dto = new DataSourceUpdateDTO();
        dto.setName("updated-name");

        when(dataSourceMapper.selectById(1L)).thenReturn(existing, afterDb);
        when(dataSourceMapper.updateById(any(DataSource.class))).thenReturn(1);

        DataSource result = dataSourceService.update(1L, dto);

        assertThat(result.getName()).isEqualTo("updated-name");
        assertThat(result.getPassword()).isNull();
        assertThat(result.getApiKey()).isNull();
    }

    @Test
    @DisplayName("删除数据源 - 无接口时成功删除")
    void delete_NoInterfaces_DeletesSuccessfully() {
        DataSource ds = new DataSource();
        ds.setId(1L);
        when(dataSourceMapper.selectById(1L)).thenReturn(ds);
        when(dataSourceMapper.countInterfacesByDataSourceId(1L)).thenReturn(0);
        when(dataSourceMapper.deleteById(1L)).thenReturn(1);

        dataSourceService.delete(1L);

        verify(dataSourceMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除数据源 - 有接口时抛出 IllegalStateException")
    void delete_HasInterfaces_ThrowsIllegalStateException() {
        DataSource ds = new DataSource();
        ds.setId(1L);
        when(dataSourceMapper.selectById(1L)).thenReturn(ds);
        when(dataSourceMapper.countInterfacesByDataSourceId(1L)).thenReturn(5);

        assertThatThrownBy(() -> dataSourceService.delete(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("5");
    }

    @Test
    @DisplayName("更新状态 - 成功变更")
    void updateStatus_ValidTransition_Updates() {
        DataSource ds = new DataSource();
        ds.setId(1L);
        when(dataSourceMapper.selectById(1L)).thenReturn(ds);
        when(dataSourceMapper.updateById(any(DataSource.class))).thenReturn(1);

        dataSourceService.updateStatus(1L, "DISABLED");

        verify(dataSourceMapper).updateById(argThat(d -> d.getId() == 1L && "DISABLED".equals(d.getStatus())));
    }
}

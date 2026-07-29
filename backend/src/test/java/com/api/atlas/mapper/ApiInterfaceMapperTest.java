package com.api.atlas.mapper;

import com.api.atlas.config.AuditInterceptor;
import com.api.atlas.model.ApiInterface;
import com.api.atlas.model.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditInterceptor.class)
class ApiInterfaceMapperTest {

    @Autowired
    private ApiInterfaceMapper apiInterfaceMapper;

    @Autowired
    private DataSourceMapper dataSourceMapper;

    private Long dataSourceId;

    @BeforeEach
    void setUp() {
        DataSource ds = new DataSource();
        ds.setName("test-ds-for-interface");
        ds.setType("MySQL");
        ds.setHost("localhost");
        ds.setPort(3306);
        ds.setStatus("ENABLED");
        dataSourceMapper.insert(ds);
        dataSourceId = ds.getId();
    }

    @Test
    @DisplayName("插入有效接口 - 返回生成的主键 ID")
    void insert_ValidData_ReturnsGeneratedId() {
        ApiInterface api = createTestInterface();
        int rows = apiInterfaceMapper.insert(api);
        assertThat(rows).isEqualTo(1);
        assertThat(api.getId()).isNotNull();
    }

    @Test
    @DisplayName("按 ID 查询 - 存在返回接口数据")
    void selectById_ExistingId_ReturnsInterface() {
        ApiInterface api = createTestInterface();
        apiInterfaceMapper.insert(api);

        ApiInterface found = apiInterfaceMapper.selectById(api.getId());
        assertThat(found).isNotNull();
        assertThat(found.getEnglishName()).isEqualTo(api.getEnglishName());
        assertThat(found.getMethod()).isEqualTo("POST");
    }

    @Test
    @DisplayName("按 ID 查询 - 不存在返回 null")
    void selectById_NonExistingId_ReturnsNull() {
        ApiInterface found = apiInterfaceMapper.selectById(9999L);
        assertThat(found).isNull();
    }

    @Test
    @DisplayName("查询列表 - 返回接口列表")
    void selectList_WithFilters_ReturnsList() {
        apiInterfaceMapper.insert(createTestInterface());
        apiInterfaceMapper.insert(createTestInterface());

        List<ApiInterface> list = apiInterfaceMapper.selectList(null, null, null);
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("按数据源查询 - 返回对应接口")
    void selectByDataSourceId_WithData_ReturnsInterfaces() {
        apiInterfaceMapper.insert(createTestInterface());

        List<ApiInterface> list = apiInterfaceMapper.selectByDataSourceId(dataSourceId);
        assertThat(list).isNotEmpty();
        assertThat(list.get(0).getDataSourceId()).isEqualTo(dataSourceId);
    }

    @Test
    @DisplayName("更新状态 - 成功变更")
    void updateStatus_ValidTransition_UpdatesStatus() {
        ApiInterface api = createTestInterface();
        apiInterfaceMapper.insert(api);

        int rows = apiInterfaceMapper.updateStatus(api.getId(), "ONLINE");
        assertThat(rows).isEqualTo(1);

        ApiInterface updated = apiInterfaceMapper.selectById(api.getId());
        assertThat(updated.getStatus()).isEqualTo("ONLINE");
    }

    @Test
    @DisplayName("删除接口 - 物理删除成功")
    void deleteById_ExistingId_DeletesSuccessfully() {
        ApiInterface api = createTestInterface();
        apiInterfaceMapper.insert(api);

        int rows = apiInterfaceMapper.deleteById(api.getId());
        assertThat(rows).isEqualTo(1);
        assertThat(apiInterfaceMapper.selectById(api.getId())).isNull();
    }

    private ApiInterface createTestInterface() {
        ApiInterface api = new ApiInterface();
        api.setEnglishName("test-api-" + System.nanoTime());
        api.setChineseName("测试接口");
        api.setUrlSlug("/test/" + System.nanoTime());
        api.setMethod("POST");
        api.setDataSourceId(dataSourceId);
        api.setQueryType("SQL");
        api.setQueryContent("SELECT * FROM test");
        api.setIsPaginated(false);
        api.setStatus("PENDING_TEST");
        return api;
    }
}

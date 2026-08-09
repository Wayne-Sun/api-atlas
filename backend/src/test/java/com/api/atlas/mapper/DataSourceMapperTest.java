package com.api.atlas.mapper;

import com.api.atlas.config.AuditInterceptor;
import com.api.atlas.model.DataSource;
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
class DataSourceMapperTest {

    @Autowired
    private DataSourceMapper dataSourceMapper;

    @Test
    @DisplayName("插入有效数据源 - 返回生成的主键 ID")
    void insert_ValidData_ReturnsGeneratedId() {
        DataSource ds = new DataSource();
        ds.setName("test-ds");
        ds.setType("MySQL");
        ds.setHost("localhost");
        ds.setPort(3306);
        ds.setStatus("DISABLED");
        int rows = dataSourceMapper.insert(ds);
        assertThat(rows).isEqualTo(1);
        assertThat(ds.getId()).isNotNull();
    }

    @Test
    @DisplayName("按 ID 查询 - 存在数据源返回完整数据")
    void selectById_ExistingId_ReturnsDataSource() {
        DataSource ds = createTestDataSource();
        dataSourceMapper.insert(ds);

        DataSource found = dataSourceMapper.selectById(ds.getId());
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo(ds.getName());
        assertThat(found.getType()).isEqualTo("MySQL");
        assertThat(found.getHost()).isEqualTo("localhost");
    }

    @Test
    @DisplayName("按 ID 查询 - 不存在返回 null")
    void selectById_NonExistingId_ReturnsNull() {
        DataSource found = dataSourceMapper.selectById(9999L);
        assertThat(found).isNull();
    }

    @Test
    @DisplayName("查询列表 - 分页返回数据源列表")
    void selectList_WithPagination_ReturnsList() {
        dataSourceMapper.insert(createTestDataSource());
        dataSourceMapper.insert(createTestDataSource());

        List<DataSource> list = dataSourceMapper.selectList(null, null, null);
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("查询全部 - 返回包含 password 列的完整数据")
    void selectAll_ReturnsPasswordColumn() {
        DataSource ds = createTestDataSource();
        ds.setPassword("encrypted-password-value");
        dataSourceMapper.insert(ds);

        List<DataSource> list = dataSourceMapper.selectAll();
        assertThat(list).isNotEmpty();
        DataSource found = list.stream()
                .filter(d -> d.getId().equals(ds.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(found.getPassword()).isEqualTo("encrypted-password-value");
    }

    @Test
    @DisplayName("更新数据源 - 字段正确更新")
    void updateById_ValidData_UpdatesFields() {
        DataSource ds = createTestDataSource();
        dataSourceMapper.insert(ds);

        ds.setHost("updated-host");
        ds.setPort(5432);
        int rows = dataSourceMapper.updateById(ds);
        assertThat(rows).isEqualTo(1);

        DataSource updated = dataSourceMapper.selectById(ds.getId());
        assertThat(updated.getHost()).isEqualTo("updated-host");
        assertThat(updated.getPort()).isEqualTo(5432);
    }

    @Test
    @DisplayName("删除数据源 - 物理删除成功")
    void deleteById_ExistingId_DeletesSuccessfully() {
        DataSource ds = createTestDataSource();
        dataSourceMapper.insert(ds);

        int rows = dataSourceMapper.deleteById(ds.getId());
        assertThat(rows).isEqualTo(1);
        assertThat(dataSourceMapper.selectById(ds.getId())).isNull();
    }

    @Test
    @DisplayName("统计接口数 - 返回正确计数")
    void countInterfacesByDataSourceId_NoInterfaces_ReturnsZero() {
        DataSource ds = createTestDataSource();
        dataSourceMapper.insert(ds);

        int count = dataSourceMapper.countInterfacesByDataSourceId(ds.getId());
        assertThat(count).isEqualTo(0);
    }

    private DataSource createTestDataSource() {
        DataSource ds = new DataSource();
        ds.setName("test-ds-" + System.nanoTime());
        ds.setType("MySQL");
        ds.setHost("localhost");
        ds.setPort(3306);
        ds.setStatus("DISABLED");
        return ds;
    }
}

package com.api.atlas.service;

import com.api.atlas.mapper.ApiInterfaceMapper;
import com.api.atlas.mapper.InterfaceParamMapper;
import com.api.atlas.model.ApiInterface;
import com.api.atlas.model.ApiInterfaceCreateDTO;
import com.api.atlas.model.ApiInterfaceUpdateDTO;
import com.api.atlas.model.InterfaceParam;
import com.api.atlas.model.ParamDef;
import com.api.atlas.service.executor.DatabaseQueryExecutor;
import com.api.atlas.service.executor.ElasticsearchQueryExecutor;
import com.api.atlas.service.executor.QueryResult;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiInterfaceServiceTest {

    @Mock
    private ApiInterfaceMapper mapper;

    @Mock
    private InterfaceParamMapper paramMapper;

    @Mock
    private ParamExtractor paramExtractor;

    @Mock
    private DatabaseQueryExecutor databaseQueryExecutor;

    @Mock
    private ElasticsearchQueryExecutor esQueryExecutor;

    @InjectMocks
    private ApiInterfaceService service;

    @Captor
    private ArgumentCaptor<ApiInterface> interfaceCaptor;

    @Captor
    private ArgumentCaptor<List<InterfaceParam>> paramListCaptor;

    // ---- Create ----

    @Test
    @DisplayName("创建接口（含参数）- 插入接口和参数记录")
    void create_ValidDTOWithParams_CreatesInterfaceAndParams() {
        // Arrange
        ApiInterfaceCreateDTO dto = new ApiInterfaceCreateDTO();
        dto.setEnglishName("Get User By Id");
        dto.setChineseName("根据ID获取用户");
        dto.setMethod("GET");
        dto.setDataSourceId(1L);
        dto.setQueryType("SQL");
        dto.setQueryContent("SELECT * FROM users WHERE id = ${userId}");
        dto.setIsPaginated(true);
        dto.setPageSize(20);

        List<ParamDef> paramDefs = List.of(new ParamDef("userId", "String", "", 0));
        when(paramExtractor.extract(dto.getQueryContent())).thenReturn(paramDefs);

        // Act
        ApiInterface result = service.create(dto);

        // Assert
        verify(mapper).insert(interfaceCaptor.capture());
        ApiInterface captured = interfaceCaptor.getValue();
        assertThat(captured.getEnglishName()).isEqualTo("Get User By Id");
        assertThat(captured.getUrlSlug()).isEqualTo("get-user-by-id");
        assertThat(captured.getStatus()).isEqualTo("PENDING_TEST");
        assertThat(captured.getIsPaginated()).isTrue();
        assertThat(captured.getPageSize()).isEqualTo(20);

        verify(paramMapper).insertBatch(paramListCaptor.capture());
        List<InterfaceParam> params = paramListCaptor.getValue();
        assertThat(params).hasSize(1);
        assertThat(params.get(0).getParamName()).isEqualTo("userId");
        assertThat(params.get(0).getJavaType()).isEqualTo("String");

        assertThat(result).isSameAs(captured);
    }

    @Test
    @DisplayName("创建接口（无参数）- 只插入接口记录")
    void create_ValidDTONoParams_CreatesInterfaceWithoutParams() {
        // Arrange
        ApiInterfaceCreateDTO dto = new ApiInterfaceCreateDTO();
        dto.setEnglishName("List All Users");
        dto.setChineseName("列出所有用户");
        dto.setMethod("GET");
        dto.setDataSourceId(1L);
        dto.setQueryType("SQL");
        dto.setQueryContent("SELECT * FROM users");

        when(paramExtractor.extract(dto.getQueryContent())).thenReturn(List.of());

        // Act
        ApiInterface result = service.create(dto);

        // Assert
        verify(mapper).insert(any(ApiInterface.class));
        verify(paramMapper, never()).insertBatch(any());
        assertThat(result.getUrlSlug()).isEqualTo("list-all-users");
        assertThat(result.getStatus()).isEqualTo("PENDING_TEST");
    }

    // ---- Query ----

    @Test
    @DisplayName("按 ID 查询 - 存在接口返回完整数据（含参数）")
    void getById_ExistingId_ReturnsInterfaceWithParams() {
        // Arrange
        ApiInterface iface = new ApiInterface();
        iface.setId(1L);
        iface.setEnglishName("test");
        when(mapper.selectById(1L)).thenReturn(iface);

        List<InterfaceParam> params = List.of(new InterfaceParam());
        when(paramMapper.selectByInterfaceId(1L)).thenReturn(params);

        // Act
        ApiInterface result = service.getById(1L);

        // Assert
        assertThat(result).isSameAs(iface);
        assertThat(result.getParams()).isSameAs(params);
        verify(mapper).selectById(1L);
        verify(paramMapper).selectByInterfaceId(1L);
    }

    @Test
    @DisplayName("按 ID 查询 - 不存在抛出 NoSuchElementException")
    void getById_NonExistingId_ThrowsNoSuchElementException() {
        when(mapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("分页查询列表 - 返回分页结果")
    void list_WithFilters_ReturnsPagedResults() {
        // Arrange
        ApiInterface iface = new ApiInterface();
        iface.setId(1L);
        List<ApiInterface> mockList = List.of(iface);
        when(mapper.selectList(1L, "test", "ONLINE")).thenReturn(mockList);

        // Act
        PageInfo<ApiInterface> result = service.list(1L, "test", "ONLINE", 1, 10);

        // Assert
        verify(mapper).selectList(1L, "test", "ONLINE");
        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getId()).isEqualTo(1L);
    }

    // ---- Update ----

    @Test
    @DisplayName("更新接口 - 更新字段并重新处理参数")
    void update_ValidDTO_UpdatesFieldsAndReprocessParams() {
        // Arrange
        ApiInterface existing = new ApiInterface();
        existing.setId(1L);
        existing.setEnglishName("Old Name");
        existing.setQueryContent("SELECT * FROM t WHERE id = ${oldParam}");
        existing.setStatus("PENDING_TEST");
        when(mapper.selectById(1L)).thenReturn(existing);

        ApiInterfaceUpdateDTO dto = new ApiInterfaceUpdateDTO();
        dto.setEnglishName("New Name");
        dto.setQueryContent("SELECT * FROM t WHERE id = ${newParam}");

        List<ParamDef> paramDefs = List.of(new ParamDef("newParam", "String", "", 0));
        when(paramExtractor.extract(dto.getQueryContent())).thenReturn(paramDefs);

        // After update, mapper.selectById returns the updated entity
        ApiInterface updated = new ApiInterface();
        updated.setId(1L);
        updated.setEnglishName("New Name");
        updated.setUrlSlug("new-name");
        when(mapper.selectById(1L)).thenReturn(existing, updated);

        // Act
        ApiInterface result = service.update(1L, dto);

        // Assert
        verify(mapper).updateById(interfaceCaptor.capture());
        ApiInterface captured = interfaceCaptor.getValue();
        assertThat(captured.getEnglishName()).isEqualTo("New Name");
        assertThat(captured.getUrlSlug()).isEqualTo("new-name");

        verify(paramMapper).deleteByInterfaceId(1L);
        verify(paramMapper).insertBatch(paramListCaptor.capture());
        assertThat(paramListCaptor.getValue()).hasSize(1);
        assertThat(paramListCaptor.getValue().get(0).getParamName()).isEqualTo("newParam");

        assertThat(result.getEnglishName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("更新接口 - 不存在抛出 NoSuchElementException")
    void update_NonExistingId_ThrowsNoSuchElementException() {
        when(mapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(999L, new ApiInterfaceUpdateDTO()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("999");
    }

    // ---- Delete ----

    @Test
    @DisplayName("删除接口 - PENDING_TEST 状态可删除")
    void delete_PendingTest_DeletesSuccessfully() {
        // Arrange
        ApiInterface iface = new ApiInterface();
        iface.setId(1L);
        iface.setStatus("PENDING_TEST");
        when(mapper.selectById(1L)).thenReturn(iface);

        // Act
        service.delete(1L);

        // Assert
        verify(paramMapper).deleteByInterfaceId(1L);
        verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除接口 - ONLINE 状态抛出 IllegalStateException")
    void delete_OnlineInterface_ThrowsIllegalStateException() {
        // Arrange
        ApiInterface iface = new ApiInterface();
        iface.setId(1L);
        iface.setStatus("ONLINE");
        when(mapper.selectById(1L)).thenReturn(iface);

        // Act & Assert
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("online")
                .hasMessageContaining("1");

        verify(paramMapper, never()).deleteByInterfaceId(any());
        verify(mapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("删除接口 - 不存在抛出 NoSuchElementException")
    void delete_NonExisting_ThrowsNoSuchElementException() {
        when(mapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("999");
    }

    // ---- Status Management ----

    @Test
    @DisplayName("更新状态 - PENDING_TEST → ONLINE 合法转换")
    void updateStatus_ValidTransition_Succeeds() {
        // Arrange
        ApiInterface iface = new ApiInterface();
        iface.setId(1L);
        iface.setStatus("PENDING_TEST");
        when(mapper.selectById(1L)).thenReturn(iface);

        // Act
        service.updateStatus(1L, "ONLINE");

        // Assert
        verify(mapper).updateStatus(1L, "ONLINE");
    }

    @Test
    @DisplayName("更新状态 - PENDING_TEST → OFFLINE 非法转换抛出异常")
    void updateStatus_InvalidTransition_ThrowsIllegalStateException() {
        // Arrange
        ApiInterface iface = new ApiInterface();
        iface.setId(1L);
        iface.setStatus("PENDING_TEST");
        when(mapper.selectById(1L)).thenReturn(iface);

        // Act & Assert
        assertThatThrownBy(() -> service.updateStatus(1L, "OFFLINE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING_TEST")
                .hasMessageContaining("OFFLINE");

        verify(mapper, never()).updateStatus(anyLong(), anyString());
    }

    // ---- Testing Interface ----

    @Test
    @DisplayName("测试接口 - OFFLINE 状态抛出 IllegalStateException")
    void testInterface_OfflineInterface_ThrowsIllegalStateException() {
        // Arrange
        ApiInterface iface = new ApiInterface();
        iface.setId(1L);
        iface.setStatus("OFFLINE");
        when(mapper.selectById(1L)).thenReturn(iface);

        // Act & Assert
        assertThatThrownBy(() -> service.testInterface(1L, Map.of(), 1, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("offline")
                .hasMessageContaining("1");
    }

    @Test
    @DisplayName("测试接口 - SQL 查询委托给 DatabaseQueryExecutor")
    void testInterface_SQLQuery_DelegatesToDatabaseExecutor() {
        // Arrange
        ApiInterface iface = new ApiInterface();
        iface.setId(1L);
        iface.setStatus("PENDING_TEST");
        iface.setDataSourceId(10L);
        iface.setQueryType("SQL");
        iface.setQueryContent("SELECT * FROM users WHERE id = ${userId}");
        when(mapper.selectById(1L)).thenReturn(iface);

        QueryResult expected = new QueryResult();
        when(databaseQueryExecutor.executeSql(10L, iface.getQueryContent(), Map.of("userId", 1), 1, 10))
                .thenReturn(expected);

        // Act
        QueryResult result = service.testInterface(1L, Map.of("userId", 1), 1, 10);

        // Assert
        assertThat(result).isSameAs(expected);
        verify(databaseQueryExecutor).executeSql(10L, iface.getQueryContent(), Map.of("userId", 1), 1, 10);
    }

    @Test
    @DisplayName("测试接口 - ESQL 查询委托给 ElasticsearchQueryExecutor")
    void testInterface_ESQLQuery_DelegatesToEsExecutor() {
        // Arrange
        ApiInterface iface = new ApiInterface();
        iface.setId(2L);
        iface.setStatus("ONLINE");
        iface.setDataSourceId(20L);
        iface.setQueryType("ESQL");
        iface.setQueryContent("SELECT * FROM index WHERE field = ${val}");
        when(mapper.selectById(2L)).thenReturn(iface);

        QueryResult expected = new QueryResult();
        when(esQueryExecutor.executeEsql(20L, iface.getQueryContent(), Map.of("val", "test"), 1, 20))
                .thenReturn(expected);

        // Act
        QueryResult result = service.testInterface(2L, Map.of("val", "test"), 1, 20);

        // Assert
        assertThat(result).isSameAs(expected);
        verify(esQueryExecutor).executeEsql(20L, iface.getQueryContent(), Map.of("val", "test"), 1, 20);
    }

    @Test
    @DisplayName("测试接口 - IBATIS 查询委托给 DatabaseQueryExecutor")
    void testInterface_IBATISQuery_DelegatesToDatabaseExecutor() {
        // Arrange
        ApiInterface iface = new ApiInterface();
        iface.setId(3L);
        iface.setStatus("PENDING_TEST");
        iface.setDataSourceId(10L);
        iface.setQueryType("IBATIS");
        iface.setQueryContent("SELECT * FROM users WHERE name = ${name}");
        when(mapper.selectById(3L)).thenReturn(iface);

        QueryResult expected = new QueryResult();
        when(databaseQueryExecutor.executeIbatis(10L, iface.getQueryContent(), Map.of("name", "foo"), 1, 10))
                .thenReturn(expected);

        // Act
        QueryResult result = service.testInterface(3L, Map.of("name", "foo"), 1, 10);

        // Assert
        assertThat(result).isSameAs(expected);
        verify(databaseQueryExecutor).executeIbatis(10L, iface.getQueryContent(), Map.of("name", "foo"), 1, 10);
    }

    @Test
    @DisplayName("测试接口 - QUERY_DSL 查询委托给 ElasticsearchQueryExecutor")
    void testInterface_QueryDsl_DelegatesToEsExecutor() {
        // Arrange
        ApiInterface iface = new ApiInterface();
        iface.setId(4L);
        iface.setStatus("ONLINE");
        iface.setDataSourceId(20L);
        iface.setQueryType("QUERY_DSL");
        iface.setQueryContent("{\"query\":{\"term\":{\"field\":\"${value}\"}}}");
        when(mapper.selectById(4L)).thenReturn(iface);

        QueryResult expected = new QueryResult();
        when(esQueryExecutor.executeQueryDsl(20L, iface.getQueryContent(), Map.of("value", "abc"), 1, 5))
                .thenReturn(expected);

        // Act
        QueryResult result = service.testInterface(4L, Map.of("value", "abc"), 1, 5);

        // Assert
        assertThat(result).isSameAs(expected);
        verify(esQueryExecutor).executeQueryDsl(20L, iface.getQueryContent(), Map.of("value", "abc"), 1, 5);
    }

    @Test
    @DisplayName("测试接口 - 不存在抛出 NoSuchElementException")
    void testInterface_NonExistingId_ThrowsNoSuchElementException() {
        when(mapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.testInterface(999L, Map.of(), 1, 10))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("测试接口 - 不支持的 queryType 抛出 IllegalArgumentException")
    void testInterface_UnsupportedQueryType_ThrowsIllegalArgumentException() {
        // Arrange
        ApiInterface iface = new ApiInterface();
        iface.setId(1L);
        iface.setStatus("PENDING_TEST");
        iface.setDataSourceId(1L);
        iface.setQueryType("UNSUPPORTED");
        iface.setQueryContent("SELECT 1");
        when(mapper.selectById(1L)).thenReturn(iface);

        // Act & Assert
        assertThatThrownBy(() -> service.testInterface(1L, Map.of(), 1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNSUPPORTED");
    }

    // ---- DataSourceEventPublisher ----

    @Test
    @DisplayName("数据源禁用回调 - 将关联接口设为 OFFLINE")
    void onDataSourceDisabled_WithInterfaces_SetsAllToOffline() {
        // Arrange
        ApiInterface iface1 = new ApiInterface();
        iface1.setId(1L);
        iface1.setStatus("ONLINE");
        ApiInterface iface2 = new ApiInterface();
        iface2.setId(2L);
        iface2.setStatus("PENDING_TEST");
        when(mapper.selectByDataSourceId(5L)).thenReturn(List.of(iface1, iface2));

        // Act
        service.onDataSourceDisabled(5L, "test-ds");

        // Assert
        verify(mapper).updateStatus(1L, "OFFLINE");
        verify(mapper).updateStatus(2L, "OFFLINE");
    }
}

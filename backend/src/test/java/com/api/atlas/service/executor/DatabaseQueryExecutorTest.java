package com.api.atlas.service.executor;

import com.api.atlas.service.DataSourceClientManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseQueryExecutorTest {

    @Mock
    private DataSourceClientManager clientManager;

    @Test
    void buildPageSql_DorisDialect_ReturnsCommaFormWithOffsetThenPageSize() {
        DatabaseQueryExecutor.PageSql pageSql =
                DatabaseQueryExecutor.buildPageSql("SELECT * FROM t WHERE a = ?", true, 20, 10);

        assertTrue(pageSql.sql().contains("LIMIT ?, ?"));
        assertArrayEquals(new Object[]{20, 10}, pageSql.pageValues());
    }

    @Test
    void buildPageSql_NonDorisDialect_ReturnsLimitOffsetFormWithPageSizeThenOffset() {
        DatabaseQueryExecutor.PageSql pageSql =
                DatabaseQueryExecutor.buildPageSql("SELECT * FROM t WHERE a = ?", false, 20, 10);

        assertTrue(pageSql.sql().contains("LIMIT ? OFFSET ?"));
        assertArrayEquals(new Object[]{10, 20}, pageSql.pageValues());
    }

    @Test
    void getOrCreateHolder_DorisType_ReturnsDorisDialectTrue() {
        when(clientManager.getDataSourceType(1L)).thenReturn("Doris");
        when(clientManager.getDataSource(1L)).thenReturn(mock(DataSource.class));

        DatabaseQueryExecutor executor = new DatabaseQueryExecutor(clientManager);
        DatabaseQueryExecutor.JdbcTemplateHolder holder = executor.getOrCreateHolder(1L);

        assertTrue(holder.dorisDialect());
        assertNotNull(holder.jdbcTemplate());
    }

    @Test
    void getOrCreateHolder_MySqlType_ReturnsDorisDialectFalse() {
        when(clientManager.getDataSourceType(2L)).thenReturn("MySQL");
        when(clientManager.getDataSource(2L)).thenReturn(mock(DataSource.class));

        DatabaseQueryExecutor executor = new DatabaseQueryExecutor(clientManager);
        DatabaseQueryExecutor.JdbcTemplateHolder holder = executor.getOrCreateHolder(2L);

        assertFalse(holder.dorisDialect());
        assertNotNull(holder.jdbcTemplate());
    }
}

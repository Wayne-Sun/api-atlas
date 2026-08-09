package com.api.atlas.service.executor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.api.atlas.service.DataSourceClientManager;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

        DatabaseQueryExecutor executor = new DatabaseQueryExecutor(clientManager, 30);
        DatabaseQueryExecutor.JdbcTemplateHolder holder = executor.getOrCreateHolder(1L);

        assertTrue(holder.dorisDialect());
        assertNotNull(holder.jdbcTemplate());
    }

    @Test
    void getOrCreateHolder_MySqlType_ReturnsDorisDialectFalse() {
        when(clientManager.getDataSourceType(2L)).thenReturn("MySQL");
        when(clientManager.getDataSource(2L)).thenReturn(mock(DataSource.class));

        DatabaseQueryExecutor executor = new DatabaseQueryExecutor(clientManager, 30);
        DatabaseQueryExecutor.JdbcTemplateHolder holder = executor.getOrCreateHolder(2L);

        assertFalse(holder.dorisDialect());
        assertNotNull(holder.jdbcTemplate());
    }

    @Test
    void getOrCreateHolder_ReturnsHolderWithQueryTimeout30() {
        when(clientManager.getDataSourceType(1L)).thenReturn("MySQL");
        when(clientManager.getDataSource(1L)).thenReturn(mock(DataSource.class));

        DatabaseQueryExecutor executor = new DatabaseQueryExecutor(clientManager, 30);
        DatabaseQueryExecutor.JdbcTemplateHolder holder = executor.getOrCreateHolder(1L);

        assertEquals(30, holder.jdbcTemplate().getQueryTimeout());
    }

    @Test
    void executeSql_InjectionParam_BindsLiteralStringAndNoDdl() {
        JdbcTemplate jt = mock(JdbcTemplate.class);
        DatabaseQueryExecutor executor = new DatabaseQueryExecutor(clientManager, 30);
        ConcurrentHashMap<Long, DatabaseQueryExecutor.JdbcTemplateHolder> cache = new ConcurrentHashMap<>();
        cache.put(1L, new DatabaseQueryExecutor.JdbcTemplateHolder(jt, false));
        ReflectionTestUtils.setField(executor, "jdbcTemplateCache", cache);

        String[] countSql = new String[1];
        Object[][] countValues = new Object[1][];
        String[] pageSql = new String[1];
        Object[][] pageValues = new Object[1][];

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            countSql[0] = (String) args[0];
            List<Object> bound = new ArrayList<>();
            for (int i = 2; i < args.length; i++) {
                if (args[i] instanceof Object[] oa) {
                    bound.addAll(Arrays.asList(oa));
                } else {
                    bound.add(args[i]);
                }
            }
            countValues[0] = bound.toArray();
            return 5L;
        }).when(jt).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            pageSql[0] = (String) args[0];
            List<Object> bound = new ArrayList<>();
            for (int i = 1; i < args.length; i++) {
                if (args[i] instanceof Object[] oa) {
                    bound.addAll(Arrays.asList(oa));
                } else {
                    bound.add(args[i]);
                }
            }
            pageValues[0] = bound.toArray();
            return List.of();
        }).when(jt).queryForList(anyString(), any(Object[].class));

        String injection = "'; DROP TABLE users;--";
        QueryResult result = executor.executeSql(1L,
                "SELECT * FROM users WHERE id = ${id} AND name = ${name}",
                Map.of("id", injection, "name", "safe"), 1, 10);

        assertNotNull(result);
        assertEquals(5L, result.getTotal());
        // Both statements reaching the JDBC layer keep ? placeholders — the
        // injection string is bound as data, never inlined.
        assertTrue(countSql[0].contains("?"));
        assertFalse(countSql[0].contains("DROP TABLE"));
        assertTrue(pageSql[0].contains("?"));
        assertFalse(pageSql[0].contains("DROP TABLE"));
        // The literal injection string is what the JdbcTemplate receives as a bound value.
        assertEquals(injection, countValues[0][0]);
        assertEquals("safe", countValues[0][1]);
        // Pagination values are bound separately after the user params.
        assertArrayEquals(new Object[]{10, 0}, pageValues[0]);
    }

    @Test
    void executeSql_JdbcTemplateThrowsDataAccessException_WrapsInIllegalArgumentException() {
        JdbcTemplate jt = mock(JdbcTemplate.class);
        when(jt.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenThrow(new BadSqlGrammarException("queryForObject",
                        "SELECT * FROM secret_table", new SQLException("bad grammar")));

        DatabaseQueryExecutor executor = new DatabaseQueryExecutor(clientManager, 30);
        ConcurrentHashMap<Long, DatabaseQueryExecutor.JdbcTemplateHolder> cache = new ConcurrentHashMap<>();
        cache.put(1L, new DatabaseQueryExecutor.JdbcTemplateHolder(jt, false));
        ReflectionTestUtils.setField(executor, "jdbcTemplateCache", cache);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.executeSql(1L, "SELECT * FROM secret_table", Map.of(), 1, 10));

        assertEquals("SQL execution failed for datasource 1", ex.getMessage());
        assertFalse(ex.getMessage().contains("secret_table"));
        assertFalse(ex.getMessage().contains("bad grammar"));
    }

    @Test
    void executeSql_JdbcTemplateThrowsDataAccessException_LogsFullDetailAtWarn() {
        JdbcTemplate jt = mock(JdbcTemplate.class);
        when(jt.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenThrow(new BadSqlGrammarException("queryForObject",
                        "SELECT * FROM secret_table", new SQLException("bad grammar")));

        DatabaseQueryExecutor executor = new DatabaseQueryExecutor(clientManager, 30);
        ConcurrentHashMap<Long, DatabaseQueryExecutor.JdbcTemplateHolder> cache = new ConcurrentHashMap<>();
        cache.put(1L, new DatabaseQueryExecutor.JdbcTemplateHolder(jt, false));
        ReflectionTestUtils.setField(executor, "jdbcTemplateCache", cache);

        ch.qos.logback.classic.Logger logbackLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(DatabaseQueryExecutor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> executor.executeSql(1L, "SELECT * FROM secret_table", Map.of(), 1, 10));

            assertFalse(appender.list.isEmpty());
            ILoggingEvent event = appender.list.get(0);
            assertEquals(Level.WARN, event.getLevel());
            assertTrue(event.getFormattedMessage().contains("SQL execution failed for datasource 1"));
            // Full cause detail still reaches the log, never the HTTP body.
            assertTrue(event.getFormattedMessage().contains("bad SQL grammar"),
                    "actual log message: [" + event.getFormattedMessage() + "]");
            assertTrue(event.getFormattedMessage().contains("secret_table"));
            assertNotNull(event.getThrowableProxy());
        } finally {
            logbackLogger.detachAppender(appender);
        }
    }

    @Test
    void executeIbatis_OverMaxRows_RethrowsIllegalArgumentException() {
        String queryContent = "SELECT id FROM demo";
        String ibatisKey = "1:" + queryContent;
        SqlSessionFactory factory = mock(SqlSessionFactory.class);
        SqlSession session = mock(SqlSession.class);

        when(clientManager.getDataSource(1L)).thenReturn(mock(DataSource.class));

        DatabaseQueryExecutor executor = new DatabaseQueryExecutor(clientManager, 30);
        ReflectionTestUtils.setField(executor, "maxMemoryRows", 5);

        Map<String, SqlSessionFactory> cache = new ConcurrentHashMap<>();
        cache.put(ibatisKey, factory);
        ReflectionTestUtils.setField(executor, "ibatisCache", cache);

        when(factory.openSession()).thenReturn(session);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ResultHandler<Map<String, Object>>> handlerCaptor =
                (ArgumentCaptor<ResultHandler<Map<String, Object>>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(ResultHandler.class);

        // Simulate MyBatis: drive the ResultHandler with 6 rows and wrap the
        // handler's IllegalArgumentException in a PersistenceException exactly
        // like DefaultSqlSession.select does via ExceptionFactory.wrapException.
        doAnswer(invocation -> {
            ResultHandler<Map<String, Object>> handler = invocation.getArgument(2);
            try {
                for (int i = 0; i < 6; i++) {
                    handler.handleResult(contextOf(Map.of("id", i)));
                }
            } catch (IllegalArgumentException limitExceeded) {
                throw new PersistenceException("Error querying database.", limitExceeded);
            }
            return null;
        }).when(session).select(anyString(), any(), handlerCaptor.capture());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.executeIbatis(1L, queryContent, Map.of(), 1, 10));

        assertTrue(ex.getMessage().contains("exceeded max-memory-rows limit 5"));
        assertTrue(ex.getMessage().contains("datasource 1"));
        assertNotNull(handlerCaptor.getValue());
        verify(session, never()).selectList(anyString(), any());
        verify(session, never()).selectList(anyString(), any(), any());
    }

    @Test
    void executeIbatis_RealFactory_ExceedingMaxRows_ThrowsIllegalArgumentException() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:executorOverLimit;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");

        JdbcTemplate jt = new JdbcTemplate(ds);
        jt.execute("CREATE TABLE IF NOT EXISTS demo_rows (id INT PRIMARY KEY, name VARCHAR(50))");
        jt.update("DELETE FROM demo_rows");
        for (int i = 0; i < 6; i++) {
            jt.update("INSERT INTO demo_rows (id, name) VALUES (?, ?)", i, "row-" + i);
        }

        when(clientManager.getDataSource(1L)).thenReturn(ds);

        DatabaseQueryExecutor executor = new DatabaseQueryExecutor(clientManager, 30);
        ReflectionTestUtils.setField(executor, "maxMemoryRows", 5);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.executeIbatis(1L, "SELECT id, name FROM demo_rows", Map.of(), 1, 10));

        assertTrue(ex.getMessage().contains("exceeded max-memory-rows limit 5"));
        assertTrue(ex.getMessage().contains("datasource 1"));
    }

    @Test
    void executeIbatis_RealFactory_UnderLimit_ReturnsRows() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:executorUnderLimit;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");

        JdbcTemplate jt = new JdbcTemplate(ds);
        jt.execute("CREATE TABLE IF NOT EXISTS demo_rows (id INT PRIMARY KEY, name VARCHAR(50))");
        jt.update("DELETE FROM demo_rows");
        for (int i = 0; i < 3; i++) {
            jt.update("INSERT INTO demo_rows (id, name) VALUES (?, ?)", i, "row-" + i);
        }

        when(clientManager.getDataSource(1L)).thenReturn(ds);

        DatabaseQueryExecutor executor = new DatabaseQueryExecutor(clientManager, 30);
        ReflectionTestUtils.setField(executor, "maxMemoryRows", 5);

        QueryResult result = executor.executeIbatis(1L,
                "SELECT id AS \"id\", name AS \"name\" FROM demo_rows", Map.of(), 1, 10);

        assertNotNull(result);
        assertEquals(3, result.getRows().size());
        assertEquals(3, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(10, result.getPageSize());
        assertEquals("row-0", result.getRows().get(0).get("name"));
    }

    private static <T> ResultContext<T> contextOf(T resultObject) {
        return new ResultContext<T>() {
            @Override
            public T getResultObject() {
                return resultObject;
            }

            @Override
            public int getResultCount() {
                return 0;
            }

            @Override
            public boolean isStopped() {
                return false;
            }

            @Override
            public void stop() {
            }
        };
    }
}

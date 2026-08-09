package com.api.atlas.service.executor;

import com.api.atlas.service.DataSourceClientManager;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DatabaseQueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryExecutor.class);

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    @Value("${atlas.executor.ibatis.max-memory-rows:100000}")
    private int maxMemoryRows;

    @Value("${atlas.executor.ibatis.cache-max-size:100}")
    private int cacheMaxSize;

    private final ConcurrentHashMap<Long, JdbcTemplateHolder> jdbcTemplateCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SqlSessionFactory> ibatisCache = new ConcurrentHashMap<>();

    private final DataSourceClientManager clientManager;

    private final int queryTimeoutSeconds;

    public DatabaseQueryExecutor(DataSourceClientManager clientManager,
                                 @Value("${atlas.executor.query-timeout-seconds:30}") int queryTimeoutSeconds) {
        this.clientManager = clientManager;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    /**
     * Cached JdbcTemplate + dialect flag resolved once at creation.
     * Package-visible so tests can observe the dialect.
     */
    static class JdbcTemplateHolder {
        private final JdbcTemplate jdbcTemplate;
        private final boolean dorisDialect;

        JdbcTemplateHolder(JdbcTemplate jdbcTemplate, boolean dorisDialect) {
            this.jdbcTemplate = jdbcTemplate;
            this.dorisDialect = dorisDialect;
        }

        JdbcTemplate jdbcTemplate() {
            return jdbcTemplate;
        }

        boolean dorisDialect() {
            return dorisDialect;
        }
    }

    /**
     * Paginated SQL + bind values in parameter order. Package-visible so tests
     * can assert the parameter-order detail (error-prone per dialect).
     */
    record PageSql(String sql, Object[] pageValues) {}

    /**
     * Build pagination SQL + bind values. Doris uses {@code LIMIT ?, ?}
     * (offset, row_count); MySQL/PostgreSQL use {@code LIMIT ? OFFSET ?}.
     * COUNT subquery is shared across dialects.
     */
    static PageSql buildPageSql(String preparedSql, boolean dorisDialect, int offset, int pageSize) {
        if (dorisDialect) {
            return new PageSql(preparedSql + " LIMIT ?, ?", new Object[]{offset, pageSize});
        }
        return new PageSql(preparedSql + " LIMIT ? OFFSET ?", new Object[]{pageSize, offset});
    }

    /**
     * Get (or lazily create) the holder for a datasource. Package-visible so
     * tests can observe the resolved dialect without reading the private cache.
     */
    JdbcTemplateHolder getOrCreateHolder(Long datasourceId) {
        return jdbcTemplateCache.computeIfAbsent(datasourceId, id -> {
            String type = clientManager.getDataSourceType(id);
            JdbcTemplate jt = new JdbcTemplate(clientManager.getDataSource(id));
            jt.setQueryTimeout(queryTimeoutSeconds);
            return new JdbcTemplateHolder(jt, "Doris".equals(type));
        });
    }

    /**
     * Execute a raw SQL query with JDBC-style pagination.
     * <p>
     * Replaces ${paramName} placeholders with JDBC positional ? parameters,
     * auto-wraps with COUNT(*) for total calculation, and applies LIMIT/OFFSET.
     */
    public QueryResult executeSql(Long datasourceId, String queryContent,
                                   Map<String, Object> params, int pageNum, int pageSize) {
        long start = System.currentTimeMillis();
        JdbcTemplateHolder holder = getOrCreateHolder(datasourceId);
        JdbcTemplate jt = holder.jdbcTemplate();

        // Replace ${paramName} with ? and collect param names in order
        List<String> paramNames = new ArrayList<>();
        String preparedSql = replacePlaceholders(queryContent, paramNames);
        Object[] paramValues = buildParamValues(paramNames, params);

        try {
            if (pageNum > 0 && pageSize > 0) {
                // Paginated: COUNT(*) + LIMIT/OFFSET (Doris uses LIMIT ?, ?)
                String countSql = "SELECT COUNT(*) FROM (" + preparedSql + ") AS total";
                Long total = jt.queryForObject(countSql, Long.class, paramValues);

                int offset = (pageNum - 1) * pageSize;
                PageSql pageSql = buildPageSql(preparedSql, holder.dorisDialect(), offset, pageSize);

                List<Map<String, Object>> rows = jt.queryForList(pageSql.sql(), pageSql.pageValues());

                QueryResult result = new QueryResult();
                result.setRows(rows);
                result.setTotal(total != null ? total : 0);
                result.setPageNum(pageNum);
                result.setPageSize(pageSize);
                result.setResponseTimeMs(System.currentTimeMillis() - start);
                return result;
            } else {
                // Non-paginated: return all rows
                List<Map<String, Object>> rows = jt.queryForList(preparedSql, paramValues);
                QueryResult result = new QueryResult();
                result.setRows(rows);
                result.setTotal(rows.size());
                result.setPageNum(1);
                result.setPageSize(rows.size());
                result.setResponseTimeMs(System.currentTimeMillis() - start);
                return result;
            }
        } catch (DataAccessException e) {
            log.warn("SQL execution failed for datasource {}: {}", datasourceId, e.getMessage(), e);
            throw new IllegalArgumentException(
                    "SQL execution failed for datasource " + datasourceId, e);
        }
    }

    /**
     * Execute a MyBatis dynamic SQL (IBATIS) query.
     * <p>
     * Wraps the user-provided MyBatis XML fragment in a mapper with a deterministic
     * namespace, parses it via {@link XMLMapperBuilder}, caches the resulting
     * {@link SqlSessionFactory}, and executes the statement.
     * <p>
     * SqlSessionFactory is cached per (datasourceId, queryContent) pair and reused
     * across calls. Factory is thread-safe for concurrent {@code openSession()}.
     */
    public QueryResult executeIbatis(Long datasourceId, String queryContent,
                                      Map<String, Object> params, int pageNum, int pageSize) {
        long start = System.currentTimeMillis();
        DataSource ds = clientManager.getDataSource(datasourceId);

        String ibatisKey = datasourceId + ":" + queryContent;
        String namespace = "dynamic.iface." + datasourceId + "." + Math.abs(ibatisKey.hashCode());

        SqlSessionFactory factory = ibatisCache.computeIfAbsent(ibatisKey, k -> {
            Configuration configuration = new Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setDefaultStatementTimeout(queryTimeoutSeconds);
            configuration.getTypeAliasRegistry().registerAlias("map", Map.class);

            String xml = buildMapperXml(namespace, queryContent);

            try {
                XMLMapperBuilder builder = new XMLMapperBuilder(
                        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                        configuration,
                        namespace + ".xml",
                        configuration.getSqlFragments());
                builder.parse();
            } catch (Exception e) {
                log.warn("IBATIS XML parse error for datasource {}: {}", datasourceId, e.getMessage(), e);
                throw new IllegalArgumentException(
                        "IBATIS XML parse error for datasource " + datasourceId, e);
            }

            configuration.setEnvironment(
                    new Environment("dynamic", new JdbcTransactionFactory(), ds));

            return new SqlSessionFactoryBuilder().build(configuration);
        });

        try (SqlSession session = factory.openSession()) {
            List<Map<String, Object>> rows = new ArrayList<>();

            // Hard memory guard: accumulate rows through a ResultHandler that
            // throws as soon as the result set exceeds maxMemoryRows. This
            // aborts iteration mid-fetch, preventing the full result set from
            // ever being materialized in memory (the previous selectList
            // approach only warned AFTER the entire result was loaded).
            ResultHandler<Map<String, Object>> resultHandler = resultContext -> {
                if (rows.size() >= maxMemoryRows) {
                    throw new IllegalArgumentException(
                            "Query exceeded max-memory-rows limit " + maxMemoryRows
                                    + " for datasource " + datasourceId);
                }
                rows.add(resultContext.getResultObject());
            };

            try {
                session.select(namespace + ".execute", params, resultHandler);
            } catch (PersistenceException e) {
                // MyBatis wraps exceptions thrown by the ResultHandler in a
                // PersistenceException (ExceptionFactory.wrapException). Unwrap
                // the limit IllegalArgumentException so it maps to 400 (see
                // GlobalExceptionHandler) instead of a generic 500.
                throw unwrapLimitExceeded(e);
            }

            // For IBATIS queries, pagination is applied in-memory since the
            // dynamic XML may contain constructs that cannot be safely wrapped.
            List<Map<String, Object>> pagedRows = rows;
            int effectivePageNum = 1;
            int effectivePageSize = rows.size();
            long total = rows.size();

            if (pageNum > 0 && pageSize > 0) {
                int fromIndex = (pageNum - 1) * pageSize;
                if (fromIndex < rows.size()) {
                    int toIndex = Math.min(fromIndex + pageSize, rows.size());
                    pagedRows = rows.subList(fromIndex, toIndex);
                } else {
                    pagedRows = List.of();
                }
                effectivePageNum = pageNum;
                effectivePageSize = pageSize;
            }

            QueryResult result = new QueryResult();
            result.setRows(pagedRows);
            result.setTotal(total);
            result.setPageNum(effectivePageNum);
            result.setPageSize(effectivePageSize);
            result.setResponseTimeMs(System.currentTimeMillis() - start);
            return result;
        }
    }

    public void clearCache(Long datasourceId) {
        jdbcTemplateCache.remove(datasourceId);
        String prefix = datasourceId + ":";
        ibatisCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * Walk the cause chain of a MyBatis {@link PersistenceException} and return
     * the limit-exceeded {@link IllegalArgumentException} thrown by the row
     * ResultHandler, if present. Otherwise return the PersistenceException.
     */
    private RuntimeException unwrapLimitExceeded(PersistenceException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException iae
                    && iae.getMessage() != null
                    && iae.getMessage().contains("exceeded max-memory-rows limit")) {
                return iae;
            }
        }
        return e;
    }

    // ---- Private helpers ----

    private String replacePlaceholders(String sql, List<String> paramNames) {
        Matcher m = PARAM_PATTERN.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "?");
            paramNames.add(m.group(1));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private Object[] buildParamValues(List<String> paramNames, Map<String, Object> params) {
        Object[] values = new Object[paramNames.size()];
        for (int i = 0; i < paramNames.size(); i++) {
            values[i] = params.get(paramNames.get(i));
        }
        return values;
    }

    private String buildMapperXml(String namespace, String queryContent) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
                + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">"
                + "<mapper namespace=\"" + namespace + "\">"
                + "  <select id=\"execute\" parameterType=\"map\" resultType=\"java.util.LinkedHashMap\">"
                + "    " + queryContent
                + "  </select>"
                + "</mapper>";
    }
}

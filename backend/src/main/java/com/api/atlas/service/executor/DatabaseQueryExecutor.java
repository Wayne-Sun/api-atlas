package com.api.atlas.service.executor;

import com.api.atlas.service.DataSourceClientManager;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DatabaseQueryExecutor {

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryExecutor.class);

    @Value("${atlas.executor.ibatis.max-memory-rows:100000}")
    private int maxMemoryRows;

    @Value("${atlas.executor.ibatis.cache-max-size:100}")
    private int cacheMaxSize;

    private final ConcurrentHashMap<Long, JdbcTemplate> jdbcTemplateCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SqlSessionFactory> ibatisCache = new ConcurrentHashMap<>();

    private final DataSourceClientManager clientManager;

    public DatabaseQueryExecutor(DataSourceClientManager clientManager) {
        this.clientManager = clientManager;
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
        JdbcTemplate jt = jdbcTemplateCache.computeIfAbsent(datasourceId,
                id -> new JdbcTemplate(clientManager.getDataSource(id)));

        // Replace ${paramName} with ? and collect param names in order
        List<String> paramNames = new ArrayList<>();
        String preparedSql = replacePlaceholders(queryContent, paramNames);
        Object[] paramValues = buildParamValues(paramNames, params);

        if (pageNum > 0 && pageSize > 0) {
            // Paginated: COUNT(*) + LIMIT/OFFSET
            String countSql = "SELECT COUNT(*) FROM (" + preparedSql + ") AS total";
            Long total = jt.queryForObject(countSql, Long.class, paramValues);

            int offset = (pageNum - 1) * pageSize;
            String pageSql = preparedSql + " LIMIT ? OFFSET ?";

            Object[] pageValues = Arrays.copyOf(paramValues, paramValues.length + 2);
            pageValues[paramValues.length] = pageSize;
            pageValues[paramValues.length + 1] = offset;

            List<Map<String, Object>> rows = jt.queryForList(pageSql, pageValues);

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
                throw new IllegalArgumentException(
                        "IBATIS XML parse error for datasource " + datasourceId + ": " + e.getMessage(), e);
            }

            configuration.setEnvironment(
                    new Environment("dynamic", new JdbcTransactionFactory(), ds));

            return new SqlSessionFactoryBuilder().build(configuration);
        });

        try (SqlSession session = factory.openSession()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) (List<?>) session.selectList(namespace + ".execute", params);

            if (rows.size() > maxMemoryRows) {
                log.warn("IBATIS query returned {} rows (limit: {}) for datasource {}, consider adding database-level pagination",
                        rows.size(), maxMemoryRows, datasourceId);
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

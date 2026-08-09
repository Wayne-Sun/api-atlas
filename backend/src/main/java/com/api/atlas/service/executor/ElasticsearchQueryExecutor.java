package com.api.atlas.service.executor;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.sql.Column;
import co.elastic.clients.elasticsearch.sql.QueryResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.api.atlas.service.DataSourceClientManager;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ElasticsearchQueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchQueryExecutor.class);

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    private final DataSourceClientManager clientManager;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public ElasticsearchQueryExecutor(DataSourceClientManager clientManager) {
        this.clientManager = clientManager;
    }

    /**
     * Execute an ES|QL query via the Elasticsearch SQL API with positional
     * {@code ${paramName}} replacement.
     * <p>
     * Placeholders are converted to {@code ?} positional markers. The response
     * columns and rows are parsed into a uniform {@link List}{@code <Map>} format.
     */
    public QueryResult executeEsql(Long datasourceId, String queryContent,
                                    Map<String, Object> params, int pageNum, int pageSize) {
        long start = System.currentTimeMillis();
        ElasticsearchClient client = clientManager.getEsClient(datasourceId);

        // Replace ${paramName} with ? and collect param names
        List<String> paramNames = new ArrayList<>();
        String preparedEsql = replacePlaceholders(queryContent, paramNames);

        java.util.Map<String, JsonData> paramMap = new java.util.HashMap<>();
        for (String name : paramNames) {
            paramMap.put(name, toJsonData(params.get(name)));
        }

        // Execute SQL query with params
        QueryResponse response;
        try {
            response = client.sql().query(q -> q
                    .query(preparedEsql)
                    .params(paramMap));
        } catch (IOException e) {
            log.warn("ES|QL execution failed for datasource {}: {}", datasourceId, e.getMessage(), e);
            throw new RuntimeException(
                    "ES|QL execution failed for datasource " + datasourceId, e);
        }

        // Parse response: columns + rows into List<Map<String, Object>>
        List<Map<String, Object>> rows = parseSqlResponse(response);

        long elapsed = System.currentTimeMillis() - start;

        QueryResult result = new QueryResult();
        result.setRows(rows);
        result.setTotal(rows.size());
        result.setPageNum(pageNum > 0 ? pageNum : 1);
        result.setPageSize(pageSize > 0 ? pageSize : rows.size());
        result.setResponseTimeMs(elapsed);
        return result;
    }

    /**
     * Execute an Elasticsearch Query DSL (JSON) with {@code ${paramName}}
     * replacement and pagination.
     * <p>
     * Walks the parsed JSON tree, replaces placeholders in text values with the
     * supplied parameter values, and submits the request via the low-level
     * {@link RestClient}. The response hits are parsed into a uniform row format.
     */
    public QueryResult executeQueryDsl(Long datasourceId, String queryContent,
                                        Map<String, Object> params, int pageNum, int pageSize) {
        long start = System.currentTimeMillis();
        ElasticsearchClient client = clientManager.getEsClient(datasourceId);

        // 1. Parse JSON and replace placeholders
        JsonNode root;
        try {
            root = objectMapper.readTree(queryContent);
        } catch (JacksonException e) {
            log.warn("Invalid Query DSL JSON for datasource {}: {}", datasourceId, e.getMessage(), e);
            throw new IllegalArgumentException(
                    "Invalid Query DSL JSON for datasource " + datasourceId, e);
        }

        if (root instanceof ObjectNode objectNode) {
            replaceParamsInObject(objectNode, params);
        } else if (root instanceof ArrayNode arrayNode) {
            replaceParamsInArray(arrayNode, params);
        }

        // 2. Extract index from the JSON body (optional field "index")
        String index = "_all";
        if (root instanceof ObjectNode on && on.has("index")) {
            index = on.get("index").asText();
            on.remove("index");
        }

        // 3. Build search endpoint with pagination
        String endpoint = "/" + index + "/_search";
        if (pageNum > 0 && pageSize > 0) {
            int from = (pageNum - 1) * pageSize;
            endpoint += "?from=" + from + "&size=" + pageSize;
        }

        // 4. Execute via low-level RestClient
        RestClient restClient = ((RestClientTransport) client._transport()).restClient();
        Request request = new Request("POST", endpoint);
        try {
            request.setJsonEntity(objectMapper.writeValueAsString(root));
        } catch (JacksonException e) {
            log.warn("Failed to serialize request JSON for datasource {}: {}", datasourceId, e.getMessage(), e);
            throw new RuntimeException(
                    "Failed to serialize request JSON for datasource " + datasourceId, e);
        }

        Response response;
        try {
            response = restClient.performRequest(request);
        } catch (IOException e) {
            log.warn("Query DSL execution failed for datasource {}: {}", datasourceId, e.getMessage(), e);
            throw new RuntimeException(
                    "Query DSL execution failed for datasource " + datasourceId, e);
        }

        // 5. Parse response
        JsonNode resultJson;
        try (InputStream body = response.getEntity().getContent()) {
            resultJson = objectMapper.readTree(body);
        } catch (IOException e) {
            log.warn("Failed to parse Query DSL response for datasource {}: {}", datasourceId, e.getMessage(), e);
            throw new RuntimeException(
                    "Failed to parse Query DSL response for datasource " + datasourceId, e);
        }

        // 6. Extract hits
        List<Map<String, Object>> rows = new ArrayList<>();
        JsonNode hitsArray = resultJson.path("hits").path("hits");
        for (JsonNode hit : hitsArray) {
            JsonNode source = hit.get("_source");
            if (source != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = objectMapper.convertValue(source, LinkedHashMap.class);
                rows.add(row);
            }
        }

        long total = resultJson.path("hits").path("total").path("value").asLong(rows.size());

        long elapsed = System.currentTimeMillis() - start;

        QueryResult queryResult = new QueryResult();
        queryResult.setRows(rows);
        queryResult.setTotal(total);
        queryResult.setPageNum(pageNum > 0 ? pageNum : 1);
        queryResult.setPageSize(pageSize > 0 ? pageSize : 10);
        queryResult.setResponseTimeMs(elapsed);
        return queryResult;
    }

    // ---- ES|QL / SQL helpers ----

    private String replacePlaceholders(String query, List<String> paramNames) {
        Matcher m = PARAM_PATTERN.matcher(query);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "?");
            paramNames.add(m.group(1));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Convert a Java value to a {@link JsonData} instance for ES SQL params.
     */
    private JsonData toJsonData(Object value) {
        if (value == null) {
            return JsonData.of(null);
        }
        if (value instanceof String s) {
            return JsonData.of(s);
        }
        if (value instanceof Number n) {
            return JsonData.of(n);
        }
        if (value instanceof Boolean b) {
            return JsonData.of(b);
        }
        return JsonData.of(value.toString());
    }

    /**
     * Parse SQL {@link QueryResponse} columns and rows into a list of maps.
     */
    private List<Map<String, Object>> parseSqlResponse(QueryResponse response) {
        List<String> columnNames = response.columns().stream()
                .map(Column::name)
                .collect(Collectors.toList());

        List<Map<String, Object>> rows = new ArrayList<>();
        List<List<JsonData>> rawRows = response.rows();
        if (rawRows != null) {
            for (List<JsonData> rowData : rawRows) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < columnNames.size() && i < rowData.size(); i++) {
                    JsonData jsonData = rowData.get(i);
                    row.put(columnNames.get(i), jsonDataToObject(jsonData));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * Convert {@link JsonData} to a plain Java object by inspecting the
     * underlying {@link JsonValue} type.
     */
    private Object jsonDataToObject(JsonData jsonData) {
        if (jsonData == null) {
            return null;
        }
        JsonValue jsonValue = jsonData.toJson();
        if (jsonValue == null) {
            return null;
        }
        switch (jsonValue.getValueType()) {
            case STRING:
                return ((JsonString) jsonValue).getString();
            case NUMBER:
                JsonNumber num = (JsonNumber) jsonValue;
                if (num.isIntegral()) {
                    return num.longValue();
                }
                return num.doubleValue();
            case TRUE:
                return Boolean.TRUE;
            case FALSE:
                return Boolean.FALSE;
            case NULL:
                return null;
            default:
                return jsonValue.toString();
        }
    }

    // ---- Query DSL JSON tree walk + replace ----

    private void replaceParamsInObject(ObjectNode node, Map<String, Object> params) {
        // Use propertyNames() which returns Collection<String> (Jackson 3.x API)
        // Collect field names first to avoid concurrent modification
        java.util.List<String> fields = java.util.stream.StreamSupport
                .stream(node.propertyNames().spliterator(), false)
                .collect(java.util.stream.Collectors.toList());
        for (String field : fields) {
            JsonNode child = node.get(field);
            if (child instanceof ObjectNode on) {
                replaceParamsInObject(on, params);
            } else if (child instanceof ArrayNode an) {
                replaceParamsInArray(an, params);
            } else if (child.isTextual()) {
                String text = child.asText();
                String replaced = replaceTextPlaceholders(text, params);
                if (!replaced.equals(text)) {
                    node.put(field, replaced);
                }
            }
        }
    }

    private void replaceParamsInArray(ArrayNode arr, Map<String, Object> params) {
        for (int i = 0; i < arr.size(); i++) {
            JsonNode child = arr.get(i);
            if (child instanceof ObjectNode on) {
                replaceParamsInObject(on, params);
            } else if (child instanceof ArrayNode an) {
                replaceParamsInArray(an, params);
            } else if (child.isTextual()) {
                String text = child.asText();
                String replaced = replaceTextPlaceholders(text, params);
                if (!replaced.equals(text)) {
                    arr.set(i, StringNode.valueOf(replaced));
                }
            }
        }
    }

    private String replaceTextPlaceholders(String text, Map<String, Object> params) {
        Matcher m = PARAM_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String paramName = m.group(1);
            Object value = params.get(paramName);
            String replacement = value != null ? Matcher.quoteReplacement(value.toString()) : "";
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }
}

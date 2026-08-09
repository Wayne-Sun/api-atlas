package com.api.atlas.service.executor;

import com.api.atlas.service.DataSourceClientManager;
import com.mongodb.MongoException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Executes MongoDB {@code find} and {@code aggregate} queries with
 * {@code ${paramName}} placeholder substitution and pagination.
 * <p>
 * Mirrors the ES executor pattern: parse the JSON body, tree-walk and replace
 * placeholders with typed nodes, then execute against the MongoDB client
 * obtained from {@link DataSourceClientManager}. The executor is read-only —
 * aggregate write stages ({@code $out}/{@code $merge}) are rejected.
 */
@Component
public class MongoQueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(MongoQueryExecutor.class);

    /** Matches a text node that consists of exactly one {@code ${name}} placeholder. */
    private static final Pattern EXACT_PARAM_PATTERN = Pattern.compile("^\\$\\{(\\w+)\\}$");

    /** Matches any {@code ${name}} placeholder inside mixed text. */
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    private static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.instance;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final DataSourceClientManager clientManager;

    public MongoQueryExecutor(DataSourceClientManager clientManager) {
        this.clientManager = clientManager;
    }

    /**
     * Execute a MongoDB {@code find} query.
     * <p>
     * Expected JSON body:
     * <pre>{@code
     * {
     *   "collection": "users",
     *   "filter": { ... },
     *   "projection": { ... },   // optional
     *   "sort": { ... }          // optional
     * }
     * }</pre>
     *
     * @param datasourceId the datasource id (MongoDB type)
     * @param queryContent the JSON query body
     * @param params       placeholder values for {@code ${paramName}}
     * @param pageNum      page number (1-based); {@code <= 0} disables pagination
     * @param pageSize     page size; {@code <= 0} disables pagination
     * @return the query result (rows, total, pagination info, response time)
     */
    public QueryResult executeFind(Long datasourceId, String queryContent,
                                   Map<String, Object> params, int pageNum, int pageSize) {
        long start = System.currentTimeMillis();
        try {
            JsonNode root = parseJson(queryContent, datasourceId);
            if (!(root instanceof ObjectNode body)) {
                throw new IllegalArgumentException(
                        "MongoDB find query must be a JSON object for datasource " + datasourceId);
            }
            replaceParamsInObject(body, params);

            String collection = extractCollection(body);
            Document filterDoc = optionalObject(body, "filter", datasourceId);
            Document projectionDoc = optionalObject(body, "projection", datasourceId);
            Document sortDoc = optionalObject(body, "sort", datasourceId);

            MongoCollection<Document> col = clientManager.getMongoDatabase(datasourceId)
                    .getCollection(collection);
            FindIterable<Document> iter = col.find(filterDoc);
            if (!projectionDoc.isEmpty()) {
                iter.projection(projectionDoc);
            }
            if (!sortDoc.isEmpty()) {
                iter.sort(sortDoc);
            }

            boolean paginated = pageNum > 0 && pageSize > 0;
            long total = 0;
            if (paginated) {
                iter.skip((pageNum - 1) * pageSize);
                iter.limit(pageSize);
                total = col.countDocuments(filterDoc);
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            MongoCursor<Document> cursor = iter.iterator();
            while (cursor.hasNext()) {
                rows.add(new LinkedHashMap<>(cursor.next()));
            }
            if (!paginated) {
                total = rows.size();
            }
            return buildResult(rows, total, pageNum, pageSize, start);
        } catch (MongoException e) {
            log.warn("MongoDB execution failed for datasource {}: {}", datasourceId, e.getMessage(), e);
            throw new RuntimeException(
                    "MongoDB execution failed for datasource " + datasourceId, e);
        }
    }

    /**
     * Execute a MongoDB {@code aggregate} query.
     * <p>
     * Expected JSON body:
     * <pre>{@code
     * {
     *   "collection": "orders",
     *   "pipeline": [ { "$match": { ... } }, ... ]
     * }
     * }</pre>
     * Pagination appends {@code $skip}/{@code $limit} to a copy of the pipeline
     * and computes the total via a second copy ending in {@code $count}.
     *
     * @param datasourceId the datasource id (MongoDB type)
     * @param queryContent the JSON query body
     * @param params       placeholder values for {@code ${paramName}}
     * @param pageNum      page number (1-based); {@code <= 0} disables pagination
     * @param pageSize     page size; {@code <= 0} disables pagination
     * @return the query result (rows, total, pagination info, response time)
     */
    public QueryResult executeAggregate(Long datasourceId, String queryContent,
                                        Map<String, Object> params, int pageNum, int pageSize) {
        long start = System.currentTimeMillis();
        try {
            JsonNode root = parseJson(queryContent, datasourceId);
            if (!(root instanceof ObjectNode body)) {
                throw new IllegalArgumentException(
                        "MongoDB aggregate query must be a JSON object for datasource " + datasourceId);
            }
            replaceParamsInObject(body, params);

            String collection = extractCollection(body);
            JsonNode pipelineNode = body.get("pipeline");
            if (!(pipelineNode instanceof ArrayNode pipeline)) {
                throw new IllegalArgumentException(
                        "MongoDB aggregate query requires a 'pipeline' array for datasource " + datasourceId);
            }
            body.remove("pipeline");

            List<Document> pipelineDocs = new ArrayList<>();
            for (JsonNode stageNode : pipeline) {
                if (stageNode instanceof ObjectNode stageObj) {
                    String firstKey = null;
                    Iterator<String> names = stageObj.propertyNames().iterator();
                    if (names.hasNext()) {
                        firstKey = names.next();
                    }
                    if ("$out".equals(firstKey) || "$merge".equals(firstKey)) {
                        throw new IllegalArgumentException(
                                "MongoDB aggregate contains write operation $" + firstKey
                                        + " for datasource " + datasourceId + " (executor is read-only)");
                    }
                }
                pipelineDocs.add(Document.parse(stageNode.toString()));
            }

            MongoCollection<Document> col = clientManager.getMongoDatabase(datasourceId)
                    .getCollection(collection);

            boolean paginated = pageNum > 0 && pageSize > 0;
            long total = 0;
            AggregateIterable<Document> resultIter;
            if (paginated) {
                // Count pipeline: original pipeline + $count -> total (never mutates the original)
                List<Document> countPipeline = new ArrayList<>(pipelineDocs);
                countPipeline.add(new Document("$count", "total"));
                Document countDoc = col.aggregate(countPipeline).first();
                if (countDoc != null && countDoc.get("total") instanceof Number n) {
                    total = n.longValue();
                }

                // Data pipeline: original pipeline + $skip + $limit
                List<Document> dataPipeline = new ArrayList<>(pipelineDocs);
                dataPipeline.add(new Document("$skip", (long) (pageNum - 1) * pageSize));
                dataPipeline.add(new Document("$limit", pageSize));
                resultIter = col.aggregate(dataPipeline);
            } else {
                resultIter = col.aggregate(pipelineDocs);
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            MongoCursor<Document> cursor = resultIter.iterator();
            while (cursor.hasNext()) {
                rows.add(new LinkedHashMap<>(cursor.next()));
            }
            if (!paginated) {
                total = rows.size();
            }
            return buildResult(rows, total, pageNum, pageSize, start);
        } catch (MongoException e) {
            log.warn("MongoDB execution failed for datasource {}: {}", datasourceId, e.getMessage(), e);
            throw new RuntimeException(
                    "MongoDB execution failed for datasource " + datasourceId, e);
        }
    }

    // ---- Shared helpers ----

    /**
     * Parse the JSON query body.
     *
     * @throws IllegalArgumentException if the JSON is invalid; the message carries
     *                                  the datasource id and the original parse error
     */
    private JsonNode parseJson(String json, Long datasourceId) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "Invalid MongoDB JSON for datasource " + datasourceId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Validate and strip the {@code collection} field from the query body.
     *
     * @throws IllegalArgumentException if missing or blank
     */
    private String extractCollection(ObjectNode body) {
        JsonNode collection = body.remove("collection");
        if (collection == null || !collection.isTextual() || collection.asText().isBlank()) {
            throw new IllegalArgumentException("MongoDB query requires a non-blank 'collection' field");
        }
        return collection.asText();
    }

    /**
     * Read an optional object field (e.g. {@code filter}/{@code projection}/{@code sort}),
     * stripping it from the body.
     *
     * @return the parsed {@link Document}, or an empty {@link Document} when absent
     * @throws IllegalArgumentException if present but not a JSON object
     */
    private Document optionalObject(ObjectNode body, String field, Long datasourceId) {
        JsonNode node = body.remove(field);
        if (node == null || node.isNull()) {
            return new Document();
        }
        if (!(node instanceof ObjectNode)) {
            throw new IllegalArgumentException(
                    "MongoDB find field '" + field + "' must be a JSON object for datasource " + datasourceId);
        }
        return jsonToDocument(node);
    }

    /** Convert a Jackson tree node to a BSON {@link Document}. */
    private Document jsonToDocument(JsonNode node) {
        return Document.parse(node.toString());
    }

    // ---- Typed placeholder substitution (tree walk) ----

    private void replaceParamsInObject(ObjectNode node, Map<String, Object> params) {
        List<String> fields = StreamSupport.stream(node.propertyNames().spliterator(), false)
                .collect(Collectors.toList());
        for (String field : fields) {
            JsonNode child = node.get(field);
            if (child instanceof ObjectNode on) {
                replaceParamsInObject(on, params);
            } else if (child instanceof ArrayNode an) {
                replaceParamsInArray(an, params);
            } else if (child.isTextual()) {
                JsonNode replacement = replaceTextNode(child.asText(), params);
                if (replacement != null) {
                    node.set(field, replacement);
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
                JsonNode replacement = replaceTextNode(child.asText(), params);
                if (replacement != null) {
                    arr.set(i, replacement);
                }
            }
        }
    }

    /**
     * Replace placeholders in a text node.
     * <p>
     * A text node that is EXACTLY {@code ${name}} is replaced wholesale with a
     * typed node (number, boolean, null or string based on the param value).
     * Mixed text containing other characters is interpolated as a string.
     *
     * @return the replacement node, or {@code null} when the text holds no placeholder
     */
    private JsonNode replaceTextNode(String text, Map<String, Object> params) {
        Matcher exact = EXACT_PARAM_PATTERN.matcher(text);
        if (exact.matches()) {
            return toTypedNode(params.get(exact.group(1)));
        }
        Matcher m = PARAM_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        do {
            String name = m.group(1);
            Object value = params.get(name);
            String replacement = value != null ? Matcher.quoteReplacement(value.toString()) : "";
            m.appendReplacement(sb, replacement);
        } while (m.find());
        m.appendTail(sb);
        return StringNode.valueOf(sb.toString());
    }

    /**
     * Map a placeholder value to a typed Jackson node.
     * <ul>
     *   <li>{@code null} / missing param → null node</li>
     *   <li>{@link Boolean} → boolean node</li>
     *   <li>{@link Integer} → int node, {@link Long} → long node, others → double node</li>
     *   <li>{@link String} "true"/"false" → boolean, "null" → null, numeric strings → number
     *       node, anything else → string node</li>
     * </ul>
     */
    private JsonNode toTypedNode(Object value) {
        if (value == null) {
            return NODE_FACTORY.nullNode();
        }
        if (value instanceof Boolean b) {
            return NODE_FACTORY.booleanNode(b);
        }
        if (value instanceof Integer i) {
            return NODE_FACTORY.numberNode(i);
        }
        if (value instanceof Long l) {
            return NODE_FACTORY.numberNode(l);
        }
        if (value instanceof Double d) {
            return NODE_FACTORY.numberNode(d);
        }
        if (value instanceof Float f) {
            return NODE_FACTORY.numberNode(f);
        }
        if (value instanceof Number n) {
            return NODE_FACTORY.numberNode(n.doubleValue());
        }
        if (value instanceof String s) {
            if ("true".equals(s)) {
                return NODE_FACTORY.booleanNode(true);
            }
            if ("false".equals(s)) {
                return NODE_FACTORY.booleanNode(false);
            }
            if ("null".equals(s)) {
                return NODE_FACTORY.nullNode();
            }
            JsonNode numeric = tryNumericString(s);
            if (numeric != null) {
                return numeric;
            }
            return StringNode.valueOf(s);
        }
        return StringNode.valueOf(value.toString());
    }

    /** Try to parse a string as int / long / double, returning the number node or {@code null}. */
    private JsonNode tryNumericString(String s) {
        try {
            return NODE_FACTORY.numberNode(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            // not an int — try long/double next
        }
        try {
            return NODE_FACTORY.numberNode(Long.parseLong(s));
        } catch (NumberFormatException e) {
            // not a long — try double next
        }
        try {
            return NODE_FACTORY.numberNode(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            // not numeric at all
        }
        return null;
    }

    private QueryResult buildResult(List<Map<String, Object>> rows, long total,
                                    int pageNum, int pageSize, long start) {
        return new QueryResult(rows, total,
                pageNum > 0 ? pageNum : 1,
                pageSize > 0 ? pageSize : rows.size(),
                System.currentTimeMillis() - start);
    }
}

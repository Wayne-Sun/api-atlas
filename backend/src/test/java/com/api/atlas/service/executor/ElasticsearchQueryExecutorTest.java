package com.api.atlas.service.executor;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.sql.ElasticsearchSqlClient;
import co.elastic.clients.elasticsearch.sql.QueryRequest;
import co.elastic.clients.elasticsearch.sql.QueryResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.util.ObjectBuilder;
import com.api.atlas.service.DataSourceClientManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElasticsearchQueryExecutorTest {

    @Mock
    private DataSourceClientManager clientManager;

    @Test
    void executeEsql_InjectionParam_FlowsThroughParamsNotInlinedInQuery() throws Exception {
        ElasticsearchClient client = mock(ElasticsearchClient.class);
        ElasticsearchSqlClient sqlClient = mock(ElasticsearchSqlClient.class);
        when(clientManager.getEsClient(1L)).thenReturn(client);
        when(client.sql()).thenReturn(sqlClient);

        QueryResponse response = mock(QueryResponse.class);
        when(response.columns()).thenReturn(List.of());
        when(response.rows()).thenReturn(List.of());

        final QueryRequest[] captured = new QueryRequest[1];
        when(sqlClient.query(any(Function.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<QueryRequest.Builder, ObjectBuilder<QueryRequest>> fn = invocation.getArgument(0);
            captured[0] = fn.apply(new QueryRequest.Builder()).build();
            return response;
        });

        ElasticsearchQueryExecutor executor = new ElasticsearchQueryExecutor(clientManager);
        String injection = "'; DROP TABLE users;--";
        QueryResult result = executor.executeEsql(1L,
                "SELECT * FROM idx WHERE id = ${id}", Map.of("id", injection), 1, 10);

        // The ESQL statement keeps the ? positional marker — the injection
        // string is never inlined into the query text.
        assertEquals("SELECT * FROM idx WHERE id = ?", captured[0].query());
        assertFalse(captured[0].query().contains("DROP TABLE"));
        // The param value flows through the .params() body as a bound literal.
        JsonData paramData = captured[0].params().get("id");
        assertEquals(injection, paramData.toString());
        assertTrue(result.getRows().isEmpty());
    }
}

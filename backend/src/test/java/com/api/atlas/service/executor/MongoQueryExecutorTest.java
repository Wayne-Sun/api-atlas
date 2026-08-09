package com.api.atlas.service.executor;

import com.api.atlas.service.DataSourceClientManager;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoQueryExecutorTest {

    @Mock
    private DataSourceClientManager clientManager;
    @Mock
    private MongoDatabase database;
    @Mock
    private MongoCollection<Document> collection;

    @InjectMocks
    private MongoQueryExecutor executor;

    @Test
    void executeFind_Paginated_ParsesFilterAndAppliesSkipLimitProjectionSort() {
        Long id = 10L;
        when(clientManager.getMongoDatabase(id)).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);

        FindIterable<Document> iter = mock(FindIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn(
                Document.parse("{\"_id\":1,\"status\":\"active\"}"),
                Document.parse("{\"_id\":2,\"status\":\"active\"}"));
        when(iter.iterator()).thenReturn(cursor);
        when(collection.find(any(Document.class))).thenReturn(iter);
        when(collection.countDocuments(any(Document.class))).thenReturn(100L);

        String query = "{\"collection\":\"users\",\"filter\":{\"status\":\"active\"},"
                + "\"projection\":{\"name\":1},\"sort\":{\"createdAt\":-1}}";
        QueryResult result = executor.executeFind(id, query, Collections.emptyMap(), 2, 10);

        assertEquals(2, result.getRows().size());
        assertEquals(100L, result.getTotal());
        assertEquals(2, result.getPageNum());
        assertEquals(10, result.getPageSize());

        verify(iter).skip(10);
        verify(iter).limit(10);
        verify(iter).projection(Document.parse("{\"name\":1}"));
        verify(iter).sort(Document.parse("{\"createdAt\":-1}"));

        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(collection).find(filterCaptor.capture());
        assertEquals(Document.parse("{\"status\":\"active\"}"), filterCaptor.getValue());
    }

    @Test
    void executeFind_NonPaginated_ReturnsAllRowsWithoutCount() {
        Long id = 11L;
        when(clientManager.getMongoDatabase(id)).thenReturn(database);
        when(database.getCollection("items")).thenReturn(collection);

        FindIterable<Document> iter = mock(FindIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(Document.parse("{\"_id\":1,\"name\":\"a\"}"));
        when(iter.iterator()).thenReturn(cursor);
        when(collection.find(any(Document.class))).thenReturn(iter);

        QueryResult result = executor.executeFind(id,
                "{\"collection\":\"items\",\"filter\":{}}", Collections.emptyMap(), 0, 0);

        assertEquals(1, result.getRows().size());
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(1, result.getPageSize());

        verify(iter, never()).skip(anyInt());
        verify(iter, never()).limit(anyInt());
        verify(collection, never()).countDocuments(any(Bson.class));
    }

    @Test
    void executeFind_InvalidJson_ThrowsIllegalArgumentExceptionWithDatasourceId() {
        Long id = 12L;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.executeFind(id, "{not json", Collections.emptyMap(), 1, 10));
        assertTrue(ex.getMessage().contains(String.valueOf(id)));
    }

    @Test
    void executeFind_MissingCollection_ThrowsIllegalArgumentException() {
        Long id = 13L;
        assertThrows(IllegalArgumentException.class,
                () -> executor.executeFind(id, "{\"filter\":{\"status\":\"active\"}}",
                        Collections.emptyMap(), 1, 10));
    }

    @SuppressWarnings("unchecked")
    @Test
    void executeAggregate_Paginated_AppendsSkipLimitAndCounts() {
        Long id = 20L;
        when(clientManager.getMongoDatabase(id)).thenReturn(database);
        when(database.getCollection("orders")).thenReturn(collection);

        AggregateIterable<Document> countIter = mock(AggregateIterable.class);
        when(countIter.first()).thenReturn(Document.parse("{\"total\":42}"));
        AggregateIterable<Document> dataIter = mock(AggregateIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(Document.parse("{\"_id\":1,\"amount\":99}"));
        when(dataIter.iterator()).thenReturn(cursor);
        when(collection.aggregate(anyList())).thenReturn(countIter, dataIter);

        String query = "{\"collection\":\"orders\",\"pipeline\":[{\"$match\":{\"status\":\"done\"}}]}";
        QueryResult result = executor.executeAggregate(id, query, Collections.emptyMap(), 2, 10);

        assertEquals(1, result.getRows().size());
        assertEquals(42L, result.getTotal());
        assertEquals(2, result.getPageNum());
        assertEquals(10, result.getPageSize());

        // First aggregate call is the $count pipeline, second is the data pipeline
        ArgumentCaptor<List> pipelineCaptor = ArgumentCaptor.forClass(List.class);
        verify(collection, times(2)).aggregate(pipelineCaptor.capture());
        List<List> pipelines = pipelineCaptor.getAllValues();
        assertEquals(2, pipelines.size());

        List<?> countPipeline = pipelines.get(0);
        assertEquals(Document.parse("{\"$match\":{\"status\":\"done\"}}"), countPipeline.get(0));
        assertEquals(new Document("$count", "total"), countPipeline.get(countPipeline.size() - 1));

        List<?> dataPipeline = pipelines.get(1);
        assertEquals(Document.parse("{\"$match\":{\"status\":\"done\"}}"), dataPipeline.get(0));
        assertEquals(new Document("$skip", 10L), dataPipeline.get(dataPipeline.size() - 2));
        assertEquals(new Document("$limit", 10), dataPipeline.get(dataPipeline.size() - 1));
    }

    @Test
    void executeAggregate_MissingPipeline_ThrowsIllegalArgumentException() {
        Long id = 21L;
        assertThrows(IllegalArgumentException.class,
                () -> executor.executeAggregate(id, "{\"collection\":\"orders\"}",
                        Collections.emptyMap(), 1, 10));
    }

    @Test
    void executeFind_ExactPlaceholders_SubstitutesTypedNodes() {
        Long id = 30L;
        when(clientManager.getMongoDatabase(id)).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);

        FindIterable<Document> iter = mock(FindIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(iter.iterator()).thenReturn(cursor);
        when(collection.find(any(Document.class))).thenReturn(iter);

        String query = "{\"collection\":\"users\",\"filter\":"
                + "{\"age\":{\"$gte\":\"${minAge}\"},\"flag\":\"${flag}\",\"note\":\"user-${id}\"}}";
        Map<String, Object> params = Map.of("minAge", "25", "flag", "true", "id", 7);
        executor.executeFind(id, query, params, 0, 0);

        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(collection).find(filterCaptor.capture());
        Document filter = (Document) filterCaptor.getValue();
        Document age = (Document) filter.get("age");
        assertEquals(25, age.get("$gte"));
        assertEquals(Boolean.TRUE, filter.get("flag"));
        assertEquals("user-7", filter.get("note"));
    }

    @Test
    void executeFind_MissingParam_SubstitutesNull() {
        Long id = 31L;
        when(clientManager.getMongoDatabase(id)).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);

        FindIterable<Document> iter = mock(FindIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(iter.iterator()).thenReturn(cursor);
        when(collection.find(any(Document.class))).thenReturn(iter);

        String query = "{\"collection\":\"users\",\"filter\":{\"score\":\"${nope}\"}}";
        executor.executeFind(id, query, Collections.emptyMap(), 0, 0);

        ArgumentCaptor<Bson> filterCaptor = ArgumentCaptor.forClass(Bson.class);
        verify(collection).find(filterCaptor.capture());
        Document filter = (Document) filterCaptor.getValue();
        assertTrue(filter.containsKey("score"));
        assertNull(filter.get("score"));
    }

    @Test
    void executeAggregate_WriteStage_ThrowsIllegalArgumentException() {
        Long id = 40L;
        IllegalArgumentException outEx = assertThrows(IllegalArgumentException.class,
                () -> executor.executeAggregate(id,
                        "{\"collection\":\"orders\",\"pipeline\":[{\"$match\":{\"a\":1}},{\"$out\":\"backup\"}]}",
                        Collections.emptyMap(), 1, 10));
        assertTrue(outEx.getMessage().contains("$out"));
        assertTrue(outEx.getMessage().contains("read-only"));

        IllegalArgumentException mergeEx = assertThrows(IllegalArgumentException.class,
                () -> executor.executeAggregate(id,
                        "{\"collection\":\"orders\",\"pipeline\":[{\"$merge\":{\"into\":\"x\"}}]}",
                        Collections.emptyMap(), 1, 10));
        assertTrue(mergeEx.getMessage().contains("$merge"));
        assertTrue(mergeEx.getMessage().contains("read-only"));
    }
}

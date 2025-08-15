package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.application.store.InMemoryHnItemStore;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RetrieveHnItemProcessorTest {

    @Test
    public void testRetrieveExistingReturnsStoredItem() throws Exception {
        // Arrange
        InMemoryHnItemStore store = new InMemoryHnItemStore();
        SerializerFactory serializerFactory = new SerializerFactory(
            List.of(new JacksonProcessorSerializer(new ObjectMapper())),
            List.of(new JacksonCriterionSerializer(new ObjectMapper()))
        );
        RetrieveHnItemProcessor processor = new RetrieveHnItemProcessor(serializerFactory, store);

        HackerNewsItem stored = new HackerNewsItem();
        stored.setId("999");
        stored.setType("story");
        stored.setOriginalJson("{\"id\":999,\"type\":\"story\",\"title\":\"Stored\",\"importTimestamp\":\"2025-01-01T00:00:00Z\"}");
        stored.setImportTimestamp(1672531200000L);
        store.upsert(stored);

        HackerNewsItem input = new HackerNewsItem();
        input.setId("999");

        ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> ctx =
            new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, input);

        // Act
        Method m = RetrieveHnItemProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        HackerNewsItem result = (HackerNewsItem) m.invoke(processor, ctx);

        // Assert
        assertNotNull(result);
        assertEquals("999", result.getId());
        assertEquals(stored.getOriginalJson(), result.getOriginalJson(), "Should return the stored originalJson exactly");
    }
}

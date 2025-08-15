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

public class SaveHnItemProcessorTest {

    @Test
    public void testSaveNewItemCreatesStoreEntry() throws Exception {
        // Arrange
        InMemoryHnItemStore store = new InMemoryHnItemStore();
        SerializerFactory serializerFactory = new SerializerFactory(
            List.of(new JacksonProcessorSerializer(new ObjectMapper())),
            List.of(new JacksonCriterionSerializer(new ObjectMapper()))
        );
        SaveHnItemProcessor processor = new SaveHnItemProcessor(serializerFactory, store);

        HackerNewsItem item = new HackerNewsItem();
        item.setId("123");
        item.setType("story");
        item.setOriginalJson("{\"id\":123,\"type\":\"story\",\"title\":\"Test\"}");

        ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> ctx =
            new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, item);

        // Act - invoke private method processEntityLogic
        Method m = SaveHnItemProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        HackerNewsItem result = (HackerNewsItem) m.invoke(processor, ctx);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getImportTimestamp(), "importTimestamp should be set on entity");
        assertTrue(result.getImportTimestamp() > 0, "importTimestamp should be a positive epoch millis");
        assertNotNull(result.getOriginalJson(), "originalJson should be present after enrichment");
        assertTrue(result.getOriginalJson().contains("importTimestamp"), "originalJson should contain importTimestamp field");

        // Ensure store contains the item
        assertTrue(store.get("123").isPresent(), "Store should contain the saved item");
    }
}

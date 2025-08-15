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

public class ProcessImportProcessorTest {

    @Test
    public void testProcessImportEnrichesAndStoresItem() throws Exception {
        InMemoryHnItemStore store = new InMemoryHnItemStore();
        SerializerFactory serializerFactory = new SerializerFactory(
            List.of(new JacksonProcessorSerializer(new ObjectMapper())),
            List.of(new JacksonCriterionSerializer(new ObjectMapper()))
        );
        ProcessImportProcessor processor = new ProcessImportProcessor(serializerFactory, store);

        HackerNewsItem item = new HackerNewsItem();
        item.setId("42");
        item.setType("story");
        item.setOriginalJson("{\"id\":42,\"type\":\"story\",\"by\":\"alice\"}");

        ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> ctx =
            new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, item);

        Method m = ProcessImportProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        HackerNewsItem result = (HackerNewsItem) m.invoke(processor, ctx);

        assertNotNull(result);
        assertNotNull(result.getImportTimestamp());
        assertTrue(result.getOriginalJson().contains("importTimestamp"));
        assertTrue(store.get("42").isPresent());
    }
}

package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MatchingProcessorTest {

    @Test
    public void testProcessSuccessful() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        EntityService entityService = mock(EntityService.class);
        // prepare one subscriber JSON
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        ObjectNode sub = JsonNodeFactory.instance.objectNode();
        sub.put("technicalId", "sub-1");
        sub.put("active", true);
        sub.put("filters", "{\"category\":\"Physics\"}");
        arr.add(sub);
        when(entityService.getItems(anyString(), anyString())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(arr));

        MatchingProcessor p = new MatchingProcessor(mock(SerializerFactory.class), entityService, objectMapper);

        Laureate l = new Laureate();
        l.setLaureateId("L1");
        l.setFullName("Alice");
        l.setCategory("Physics");
        l.setYear(2020);

        Method m = MatchingProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Object res = m.invoke(p, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext(null, l));
        assertTrue(res instanceof Laureate);
        Laureate out = (Laureate) res;
        // sourceRecord should contain matchedSubscribers
        assertNotNull(out.getSourceRecord());
        assertTrue(out.getSourceRecord().contains("matchedSubscribers"));
    }
}

package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BackfillProcessorTest {

    @Test
    public void testProcessSuccessful() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        EntityService entityService = mock(EntityService.class);
        // return empty array for getItemsByCondition
        ArrayNode empty = JsonNodeFactory.instance.arrayNode();
        when(entityService.getItemsByCondition(anyString(), anyString(), any(), anyBoolean()))
            .thenReturn(CompletableFuture.completedFuture(empty));

        BackfillProcessor processor = new BackfillProcessor(mock(SerializerFactory.class), entityService, objectMapper);

        Subscriber s = new Subscriber();
        s.setTechnicalId("sub-1");
        s.setActive(true);
        s.setBackfillFromDate("2000-01-01");

        Method m = BackfillProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Object res = m.invoke(processor, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext(null, s));
        assertTrue(res instanceof Subscriber);
        Subscriber out = (Subscriber) res;
        assertNotNull(out.getNotificationHistory());
        assertTrue(out.getNotificationHistory().contains("backfillMatches"));
    }
}

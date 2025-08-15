package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class PersistItemProcessorTest {

    @Test
    public void testPersistNewItem() throws Exception {
        SerializerFactory serializerFactory = Mockito.mock(SerializerFactory.class);
        ProcessorSerializer dummySerializer = Mockito.mock(ProcessorSerializer.class);
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(dummySerializer);

        EntityService entityService = Mockito.mock(EntityService.class);
        when(entityService.getItemsByCondition(any(), any(), any(), eq(true)))
            .thenReturn(CompletableFuture.completedFuture(JsonNodeFactory.instance.arrayNode()));
        when(entityService.addItem(eq("HNItem"), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        PersistItemProcessor processor = new PersistItemProcessor(serializerFactory, entityService);

        HNItem hn = new HNItem();
        hn.setStatus("ENRICHED");
        hn.setRawJson("{\"id\": 999, \"type\": \"story\"}");
        hn.setId(999L);

        ProcessorSerializer.ProcessorEntityExecutionContext<?> ctxProxy = (ProcessorSerializer.ProcessorEntityExecutionContext<?>) Proxy.newProxyInstance(
            ProcessorSerializer.ProcessorEntityExecutionContext.class.getClassLoader(),
            new Class[]{ProcessorSerializer.ProcessorEntityExecutionContext.class},
            (proxy, method, args) -> {
                if ("entity".equals(method.getName())) return hn;
                return null;
            }
        );

        Method m = PersistItemProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        HNItem result = (HNItem) m.invoke(processor, ctxProxy);

        assertNotNull(result);
        assertEquals("STORED", result.getStatus());
        assertNotNull(result.getTechnicalId());
        assertNotNull(result.getCreatedAt());
    }
}

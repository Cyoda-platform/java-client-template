package com.java_template.application.processor;

import com.java_template.application.entity.ingestjob.version_1.IngestJob;
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

public class SplitPayloadProcessorTest {

    @Test
    public void testSplitArrayPayload() throws Exception {
        SerializerFactory serializerFactory = Mockito.mock(SerializerFactory.class);
        ProcessorSerializer dummySerializer = Mockito.mock(ProcessorSerializer.class);
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(dummySerializer);

        EntityService entityService = Mockito.mock(EntityService.class);
        when(entityService.addItem(eq("HNItem"), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        SplitPayloadProcessor processor = new SplitPayloadProcessor(serializerFactory, entityService);

        IngestJob job = new IngestJob();
        job.setPayload("[{\"id\":1,\"type\":\"story\"},{\"id\":2,\"type\":\"comment\"}]");

        ProcessorSerializer.ProcessorEntityExecutionContext<?> ctxProxy = (ProcessorSerializer.ProcessorEntityExecutionContext<?>) Proxy.newProxyInstance(
            ProcessorSerializer.ProcessorEntityExecutionContext.class.getClassLoader(),
            new Class[]{ProcessorSerializer.ProcessorEntityExecutionContext.class},
            (proxy, method, args) -> {
                if ("entity".equals(method.getName())) return job;
                return null;
            }
        );

        Method m = SplitPayloadProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        IngestJob result = (IngestJob) m.invoke(processor, ctxProxy);

        assertNotNull(result.getCreatedItemTechnicalIds());
        assertEquals(2, result.getCreatedItemTechnicalIds().size());
        assertEquals("ITEMS_ENQUEUED", result.getStatus());
    }
}

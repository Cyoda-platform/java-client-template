package com.java_template.application.processor;

import com.java_template.application.entity.ingestjob.version_1.IngestJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class EnqueueItemsProcessorTest {

    @Test
    public void testEnqueueMarksItemsReceived() throws Exception {
        SerializerFactory serializerFactory = Mockito.mock(SerializerFactory.class);
        ProcessorSerializer dummySerializer = Mockito.mock(ProcessorSerializer.class);
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(dummySerializer);

        // no external calls needed for this processor
        EnqueueItemsProcessor processor = new EnqueueItemsProcessor(serializerFactory, null);

        // prepare repo item
        HNItem item = new HNItem();
        item.setTechnicalId("tid-1");
        item.setStatus("RECEIVED");
        HNItemRepository.getInstance().save(item);

        IngestJob job = new IngestJob();
        job.setCreatedItemTechnicalIds(Arrays.asList("tid-1"));

        ProcessorSerializer.ProcessorEntityExecutionContext<?> ctxProxy = (ProcessorSerializer.ProcessorEntityExecutionContext<?>) Proxy.newProxyInstance(
            ProcessorSerializer.ProcessorEntityExecutionContext.class.getClassLoader(),
            new Class[]{ProcessorSerializer.ProcessorEntityExecutionContext.class},
            (proxy, method, args) -> {
                if ("entity".equals(method.getName())) return job;
                return null;
            }
        );

        Method m = EnqueueItemsProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        IngestJob result = (IngestJob) m.invoke(processor, ctxProxy);

        assertEquals("ITEMS_ENQUEUED", result.getStatus());
        HNItem stored = HNItemRepository.getInstance().findByTechnicalId("tid-1");
        assertNotNull(stored);
        assertEquals("RECEIVED", stored.getStatus());
    }
}

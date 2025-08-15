package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PersistLaureateProcessorTest {

    @Test
    public void testPersistSuccess() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        ProcessorSerializer ps = Mockito.mock(ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EntityService es = Mockito.mock(EntityService.class);
        Mockito.when(es.addItem(Mockito.eq(Laureate.ENTITY_NAME), Mockito.eq(String.valueOf(Laureate.ENTITY_VERSION)), Mockito.any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        PersistLaureateProcessor processor = new PersistLaureateProcessor(sf, es);

        Laureate l = new Laureate();
        l.setId(10);
        l.setFirstname("Jane");
        l.setSurname("Doe");
        l.setYear("1999");
        l.setCategory("Peace");

        Method m = PersistLaureateProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, l);
        Laureate result = (Laureate) m.invoke(processor, ctx);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("STORED", result.getStatus());
        Assertions.assertNull(result.getLastError());
    }
}

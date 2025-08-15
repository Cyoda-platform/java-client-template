package com.java_template.application.processor;

import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import com.java_template.common.workflow.CyodaEventContext;
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

public class ValidateRequiredFieldsProcessorTest {

    @Test
    public void testValidateSuccess() throws Exception {
        // mocks
        SerializerFactory serializerFactory = Mockito.mock(SerializerFactory.class);
        ProcessorSerializer dummySerializer = Mockito.mock(ProcessorSerializer.class);
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(dummySerializer);

        EntityService entityService = Mockito.mock(EntityService.class);
        when(entityService.addItem(eq("ValidationRecord"), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // instantiate processor
        ValidateRequiredFieldsProcessor processor = new ValidateRequiredFieldsProcessor(serializerFactory, entityService);

        // prepare entity with valid rawJson
        HNItem hn = new HNItem();
        hn.setRawJson("{\"id\": 123, \"type\": \"story\"}");

        // create a dynamic proxy for ProcessorSerializer.ProcessorEntityExecutionContext
        ProcessorSerializer.ProcessorEntityExecutionContext<?> ctxProxy = (ProcessorSerializer.ProcessorEntityExecutionContext<?>) Proxy.newProxyInstance(
            ProcessorSerializer.ProcessorEntityExecutionContext.class.getClassLoader(),
            new Class[]{ProcessorSerializer.ProcessorEntityExecutionContext.class},
            (proxy, method, args) -> {
                if ("entity".equals(method.getName())) return hn;
                return null;
            }
        );

        // invoke private method
        Method m = ValidateRequiredFieldsProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        HNItem result = (HNItem) m.invoke(processor, ctxProxy);

        assertNotNull(result);
        assertEquals("VALIDATED", result.getStatus());
        assertNull(result.getErrorMessage());
        assertEquals(123L, result.getId());
    }
}

package com.java_template.application.processor;

import com.java_template.application.entity.validationrecord.version_1.ValidationRecord;
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

public class PersistValidationRecordProcessorTest {

    @Test
    public void testPersistValidationRecord() throws Exception {
        SerializerFactory serializerFactory = Mockito.mock(SerializerFactory.class);
        ProcessorSerializer dummySerializer = Mockito.mock(ProcessorSerializer.class);
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(dummySerializer);

        EntityService entityService = Mockito.mock(EntityService.class);
        when(entityService.addItem(eq("ValidationRecord"), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        PersistValidationRecordProcessor processor = new PersistValidationRecordProcessor(serializerFactory, entityService);

        ValidationRecord rec = new ValidationRecord();
        rec.setHnItemId(1L);

        ProcessorSerializer.ProcessorEntityExecutionContext<?> ctxProxy = (ProcessorSerializer.ProcessorEntityExecutionContext<?>) Proxy.newProxyInstance(
            ProcessorSerializer.ProcessorEntityExecutionContext.class.getClassLoader(),
            new Class[]{ProcessorSerializer.ProcessorEntityExecutionContext.class},
            (proxy, method, args) -> {
                if ("entity".equals(method.getName())) return rec;
                return null;
            }
        );

        Method m = PersistValidationRecordProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        ValidationRecord result = (ValidationRecord) m.invoke(processor, ctxProxy);

        assertNotNull(result.getTechnicalId());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getCheckedAt());
    }
}

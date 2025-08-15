package com.java_template.application.processor;

import com.java_template.application.entity.lead.version_1.Lead;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public class QualifyLeadProcessorTest {

    @Test
    public void testProcess_qualifiesLeadAndPersists() throws Exception {
        SerializerFactory sf = mock(SerializerFactory.class);
        ProcessorSerializer ps = mock(ProcessorSerializer.class);
        when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EntityService entityService = mock(EntityService.class);
        when(entityService.updateItem(anyString(), anyString(), any(UUID.class), any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        QualifyLeadProcessor processor = new QualifyLeadProcessor(sf, entityService);

        Lead lead = new Lead();
        lead.setEmail("lead@acme.com");
        lead.setCompany("Acme");

        EntityProcessorCalculationRequest request = mock(EntityProcessorCalculationRequest.class);
        String technicalId = UUID.randomUUID().toString();
        when(request.getEntityId()).thenReturn(technicalId);

        ProcessorSerializer.ProcessorEntityExecutionContext<Lead> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(request, lead);

        Method m = QualifyLeadProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Lead result = (Lead) m.invoke(processor, ctx);

        assertNotNull(result);
        assertEquals("QUALIFIED", result.getStatus());
        verify(entityService).updateItem(eq(Lead.ENTITY_NAME), eq(String.valueOf(Lead.ENTITY_VERSION)), eq(UUID.fromString(technicalId)), any());
    }
}

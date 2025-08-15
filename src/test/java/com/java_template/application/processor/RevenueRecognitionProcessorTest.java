package com.java_template.application.processor;

import com.java_template.application.entity.opportunity.version_1.Opportunity;
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

public class RevenueRecognitionProcessorTest {

    @Test
    public void testProcess_transitionsClosedWonToRevenueProcessed() throws Exception {
        SerializerFactory sf = mock(SerializerFactory.class);
        ProcessorSerializer ps = mock(ProcessorSerializer.class);
        when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EntityService entityService = mock(EntityService.class);
        when(entityService.updateItem(anyString(), anyString(), any(UUID.class), any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        RevenueRecognitionProcessor processor = new RevenueRecognitionProcessor(sf, entityService);

        Opportunity opp = new Opportunity();
        opp.setName("Won Deal");
        opp.setAmount(10000.0);
        opp.setStage("CLOSED_WON");

        EntityProcessorCalculationRequest request = mock(EntityProcessorCalculationRequest.class);
        String technicalId = UUID.randomUUID().toString();
        when(request.getEntityId()).thenReturn(technicalId);

        ProcessorSerializer.ProcessorEntityExecutionContext<Opportunity> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(request, opp);

        Method m = RevenueRecognitionProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Opportunity result = (Opportunity) m.invoke(processor, ctx);

        assertNotNull(result);
        assertEquals("REVENUE_PROCESSED", result.getStage());
        verify(entityService).updateItem(eq(Opportunity.ENTITY_NAME), eq(String.valueOf(Opportunity.ENTITY_VERSION)), eq(UUID.fromString(technicalId)), any());
    }
}

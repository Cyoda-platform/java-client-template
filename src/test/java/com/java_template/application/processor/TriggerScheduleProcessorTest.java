package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
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

public class TriggerScheduleProcessorTest {

    @Test
    public void testProcess_setsInProgressAndPersists() throws Exception {
        SerializerFactory sf = mock(SerializerFactory.class);
        ProcessorSerializer ps = mock(ProcessorSerializer.class);
        when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EntityService entityService = mock(EntityService.class);
        when(entityService.updateItem(anyString(), anyString(), any(UUID.class), any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        TriggerScheduleProcessor processor = new TriggerScheduleProcessor(sf, entityService);

        Job job = new Job();
        job.setName("job-trigger");
        job.setTargetEntity("Lead");
        job.setStatus("SCHEDULED");

        EntityProcessorCalculationRequest request = mock(EntityProcessorCalculationRequest.class);
        String technicalId = UUID.randomUUID().toString();
        when(request.getEntityId()).thenReturn(technicalId);

        ProcessorSerializer.ProcessorEntityExecutionContext<Job> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(request, job);

        Method m = TriggerScheduleProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Job result = (Job) m.invoke(processor, ctx);

        assertNotNull(result);
        assertEquals("IN_PROGRESS", result.getStatus());
        verify(entityService).updateItem(eq(Job.ENTITY_NAME), eq(String.valueOf(Job.ENTITY_VERSION)), eq(UUID.fromString(technicalId)), any());
    }
}

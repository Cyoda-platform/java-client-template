package com.java_template.application.processor;

import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import com.java_template.common.service.EntityService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class StartFetchProcessorTest {

    @Test
    public void testStartSetsRunningAndStartedAt() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        com.java_template.common.serializer.ProcessorSerializer ps = Mockito.mock(com.java_template.common.serializer.ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        StartFetchProcessor processor = new StartFetchProcessor(sf, Mockito.mock(EntityService.class));
        FetchJob job = new FetchJob();
        job.setRequestDate("2025-03-25");
        job.setScheduledTime("18:00Z");

        Method m = StartFetchProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        FetchJob out = (FetchJob) m.invoke(processor, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<>(null, job));

        assertEquals("RUNNING", out.getStatus());
        assertNotNull(out.getStartedAt());
    }
}

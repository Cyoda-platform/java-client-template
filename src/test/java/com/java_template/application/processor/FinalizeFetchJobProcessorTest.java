package com.java_template.application.processor;

import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class FinalizeFetchJobProcessorTest {

    @Test
    public void testFinalizeSetsCompleted() throws Exception {
        SerializerFactory sf = org.mockito.Mockito.mock(SerializerFactory.class);
        com.java_template.common.serializer.ProcessorSerializer ps = org.mockito.Mockito.mock(com.java_template.common.serializer.ProcessorSerializer.class);
        org.mockito.Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        FinalizeFetchJobProcessor processor = new FinalizeFetchJobProcessor(sf);
        FetchJob job = new FetchJob();
        job.setRequestDate("2025-03-25");
        job.setScheduledTime("18:00Z");
        job.setStatus("RUNNING");

        Method m = FinalizeFetchJobProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        FetchJob out = (FetchJob) m.invoke(processor, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<>(null, job));

        assertNotNull(out);
        assertEquals("COMPLETED", out.getStatus());
        assertNotNull(out.getCompletedAt());
    }
}

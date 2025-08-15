package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class JobRetryProcessorTest {

    @Test
    public void testProcessSuccessful() throws Exception {
        JobRetryProcessor p = new JobRetryProcessor(mock(SerializerFactory.class));
        Job job = new Job();
        job.setTechnicalId("job-1");
        job.setStatus("FAILED");
        job.setRetryCount(0);
        job.setMaxRetries(2);

        Method m = JobRetryProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Object res = m.invoke(p, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext(null, job));
        assertTrue(res instanceof Job);
        Job out = (Job) res;
        assertEquals("RETRY_WAIT", out.getStatus());
        assertNotNull(out.getLastRunAt());
        assertEquals(1, out.getRetryCount());
    }
}

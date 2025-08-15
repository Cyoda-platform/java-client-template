package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class JobFetchProcessorTest {

    @Test
    public void testProcessSuccessful() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JobFetchProcessor p = new JobFetchProcessor(mock(SerializerFactory.class), objectMapper);

        Job job = new Job();
        job.setTechnicalId("job-1");
        job.setStatus("FETCHING");
        job.setSourceUrl("https://example.com");

        Method m = JobFetchProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Object res = m.invoke(p, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext(null, job));
        assertTrue(res instanceof Job);
        Job out = (Job) res;
        assertEquals("NORMALIZING", out.getStatus());
        assertNotNull(out.getResultSummary());
    }

}

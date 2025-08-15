package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class NormalizeProcessorTest {

    @Test
    public void testProcessSuccessful() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        NormalizeProcessor p = new NormalizeProcessor(mock(SerializerFactory.class), objectMapper);

        Job job = new Job();
        job.setTechnicalId("job-1");
        job.setStatus("NORMALIZING");

        Method m = NormalizeProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Object res = m.invoke(p, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext(null, job));
        assertTrue(res instanceof Job);
        Job out = (Job) res;
        assertEquals("COMPARING", out.getStatus());
        assertNotNull(out.getResultSummary());
        assertTrue(out.getResultSummary().contains("normalizedCount"));
    }
}

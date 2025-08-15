package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class JobValidationProcessorTest {

    @Test
    public void testProcessSuccessful() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        EntityService entityService = mock(EntityService.class);
        JobValidationProcessor p = new JobValidationProcessor(mock(SerializerFactory.class), entityService, objectMapper);

        Job job = new Job();
        job.setTechnicalId("job-1");
        job.setName("Test Job");
        job.setSourceUrl("https://example.com/data");
        job.setSchedule("0 0 * * *");
        job.setManual(false);
        job.setTransformRules("{\"rule\":\"v\"}");
        job.setCreatedBy("tester");

        Method m = JobValidationProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Object res = m.invoke(p, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext(null, job));
        assertTrue(res instanceof Job);
        Job out = (Job) res;
        assertEquals("FETCHING", out.getStatus());
        assertNotNull(out.getResultSummary());
    }
}

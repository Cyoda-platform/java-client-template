package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class DedupAndVersionProcessorTest {

    @Test
    public void testProcessSuccessful() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        DedupAndVersionProcessor processor = new DedupAndVersionProcessor(mock(SerializerFactory.class), objectMapper);

        Job job = new Job();
        job.setTechnicalId("job-1");
        job.setStatus("COMPARING");
        // set a resultSummary with normalizedSamples
        String rs = "{\"normalizedSamples\": [{\"laureateId\": \"l1\", \"fullName\": \"A\", \"year\": 2020, \"category\": \"Physics\"}]}";
        job.setResultSummary(rs);

        Method m = DedupAndVersionProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Object res = m.invoke(processor, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext(null, job));
        assertTrue(res instanceof Job);
        Job out = (Job) res;
        assertEquals("PERSISTING", out.getStatus());
        assertNotNull(out.getResultSummary());
        assertTrue(out.getResultSummary().contains("toPersistCount"));
    }

    // helper to create a mock SerializerFactory without pulling full Spring context
    private SerializerFactory mock(SerializerFactory sf) {
        return sf;
    }
}

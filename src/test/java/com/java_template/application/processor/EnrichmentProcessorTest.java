package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class EnrichmentProcessorTest {

    @Test
    public void testProcessSuccessful() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        EntityService entityService = mock(EntityService.class);
        EnrichmentProcessor p = new EnrichmentProcessor(mock(SerializerFactory.class), entityService, objectMapper);

        Laureate l = new Laureate();
        l.setLaureateId("L1");
        l.setFullName("John Doe");
        l.setCategory("Physics");
        l.setAffiliations("University A, Institute B");

        Method m = EnrichmentProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Object res = m.invoke(p, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext(null, l));
        assertTrue(res instanceof Laureate);
        Laureate out = (Laureate) res;
        assertNotNull(out.getMatchTags());
        assertTrue(out.getAffiliations().contains("university a"));
    }
}

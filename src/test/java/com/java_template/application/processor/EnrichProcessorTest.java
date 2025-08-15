package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

public class EnrichProcessorTest {

    @Test
    public void testEnrichmentSuccess() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        ProcessorSerializer ps = Mockito.mock(ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EnrichProcessor processor = new EnrichProcessor(sf);

        Laureate l = new Laureate();
        l.setId(1);
        l.setFirstname("John");
        l.setSurname("Doe");
        l.setYear("2000");
        l.setBorn("1970-05-20");
        l.setBorncountrycode("us");
        l.setStatus("VALIDATED");

        Method m = EnrichProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, l);
        Laureate result = (Laureate) m.invoke(processor, ctx);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(Integer.valueOf(30), result.getAgeAtAward());
        Assertions.assertEquals("US", result.getNormalizedCountryCode());
        Assertions.assertEquals("ENRICHED", result.getStatus());
    }
}

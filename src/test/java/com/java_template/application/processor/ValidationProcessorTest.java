package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

public class ValidationProcessorTest {

    @Test
    public void testValidationSuccess() throws Exception {
        // Arrange: create a serializer factory mock required by constructor
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        ProcessorSerializer ps = Mockito.mock(ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        ValidationProcessor processor = new ValidationProcessor(sf);

        Laureate l = new Laureate();
        l.setId(853);
        l.setFirstname("Akira");
        l.setSurname("Suzuki");
        l.setYear("2010");
        l.setCategory("Chemistry");

        // Invoke private method processEntityLogic via reflection
        Method m = ValidationProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, l);
        Laureate result = (Laureate) m.invoke(processor, ctx);

        // Assert
        Assertions.assertNotNull(result);
        Assertions.assertEquals("VALIDATED", result.getStatus());
        Assertions.assertTrue(result.getValidations() == null || result.getValidations().isEmpty());
    }
}

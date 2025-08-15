package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class AutoActivateProcessorTest {

    @Test
    public void testSuccessfulProcessing() throws Exception {
        SerializerFactory serializerFactory = Mockito.mock(SerializerFactory.class);
        ProcessorSerializer processorSerializer = Mockito.mock(ProcessorSerializer.class);
        Mockito.when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(processorSerializer);

        AutoActivateProcessor processor = new AutoActivateProcessor(serializerFactory);

        Subscriber s = new Subscriber();
        s.setEmail("user@example.com");
        s.setConfirmed(false);
        s.setStatus("pending");

        Method m = AutoActivateProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Subscriber out = (Subscriber) m.invoke(processor, new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, s));

        // Current implementation keeps requiresConfirmation=true, so no auto activation; entity should remain pending and unconfirmed
        assertNotNull(out);
        assertEquals("pending", out.getStatus());
        assertFalse(out.getConfirmed());
    }
}

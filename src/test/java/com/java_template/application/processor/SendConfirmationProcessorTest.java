package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

public class SendConfirmationProcessorTest {

    @Test
    public void testGenerateConfirmationToken() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        com.java_template.common.serializer.ProcessorSerializer ps = Mockito.mock(com.java_template.common.serializer.ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EntityService entityService = Mockito.mock(EntityService.class);
        JsonUtils jsonUtils = Mockito.mock(JsonUtils.class);

        SendConfirmationProcessor processor = new SendConfirmationProcessor(sf, entityService, jsonUtils);

        Subscriber s = new Subscriber();
        s.setEmail("user@example.com");
        s.setPreferences("{}");

        java.lang.reflect.Method m = SendConfirmationProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Subscriber out = (Subscriber) m.invoke(processor, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<>(null, s));

        assertEquals("pending", out.getStatus());
        assertFalse(out.getConfirmed());
    }
}

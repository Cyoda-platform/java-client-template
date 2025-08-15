package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class UnsubscribeRequestProcessorTest {

    @Test
    public void testUnsubscribeSetsStatus() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        com.java_template.common.serializer.ProcessorSerializer ps = Mockito.mock(com.java_template.common.serializer.ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        JsonUtils jsonUtils = Mockito.mock(JsonUtils.class);
        UnsubscribeRequestProcessor processor = new UnsubscribeRequestProcessor(sf, jsonUtils);

        Subscriber s = new Subscriber();
        s.setEmail("user@example.com");
        s.setStatus("active");

        Method m = UnsubscribeRequestProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Subscriber out = (Subscriber) m.invoke(processor, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<>(null, s));

        assertEquals("unsubscribed", out.getStatus());
        assertNotNull(out.getLastNotificationAt());
    }
}

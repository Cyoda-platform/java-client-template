package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

public class SubscriberVerificationProcessorTest {

    @Test
    public void testVerificationMarksActive() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        ProcessorSerializer ps = Mockito.mock(ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EntityService es = Mockito.mock(EntityService.class);
        ObjectMapper om = new ObjectMapper();

        SubscriberVerificationProcessor processor = new SubscriberVerificationProcessor(sf, es, om);

        Subscriber s = new Subscriber();
        s.setName("Test Sub");
        s.setChannels(java.util.List.of("WEBHOOK"));
        s.setEmail("test@example.com");

        Method m = SubscriberVerificationProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, s);
        Subscriber result = (Subscriber) m.invoke(processor, ctx);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.getActive());
        Assertions.assertNotNull(result.getVerifiedAt());
    }
}

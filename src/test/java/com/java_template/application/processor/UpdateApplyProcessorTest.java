package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class UpdateApplyProcessorTest {

    @Test
    public void testProcessSuccessful() throws Exception {
        UpdateApplyProcessor p = new UpdateApplyProcessor(mock(SerializerFactory.class));
        Subscriber s = new Subscriber();
        s.setTechnicalId("sub-1");
        Method m = UpdateApplyProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Object res = m.invoke(p, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext(null, s));
        assertTrue(res instanceof Subscriber);
        Subscriber out = (Subscriber) res;
        assertNotNull(out.getUpdatedAt());
    }
}

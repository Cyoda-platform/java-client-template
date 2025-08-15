package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class ScheduleNotificationProcessorTest {

    @Test
    public void testProcessSuccessful() throws Exception {
        ScheduleNotificationProcessor p = new ScheduleNotificationProcessor(mock(SerializerFactory.class), new ObjectMapper());
        Laureate l = new Laureate();
        l.setLaureateId("L1");
        // sourceRecord with matchedSubscribers array
        l.setSourceRecord("{\"matchedSubscribers\":[\"sub-1\"]}");
        Method m = ScheduleNotificationProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Object res = m.invoke(p, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext(null, l));
        assertTrue(res instanceof Laureate);
        Laureate out = (Laureate) res;
        assertNotNull(out.getProvenance());
        assertTrue(out.getProvenance().containsKey("notifications"));
    }
}

package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationDispatchProcessorTest {

    @Test
    public void testProcessSuccessful() throws Exception {
        NotificationDispatchProcessor p = new NotificationDispatchProcessor(mock(SerializerFactory.class));
        Laureate l = new Laureate();
        l.setLaureateId("L1");
        // set provenance map with notifications (as a Map stored in provenance field) - but provenance is not present in entity, so use sourceRecord
        java.util.Map<String, Object> prov = new java.util.HashMap<>();
        java.util.List<java.util.Map<String, Object>> notifs = new java.util.ArrayList<>();
        notifs.add(Map.of("notificationId","n1","subscriberId","s1","deliveryPreference","IMMEDIATE"));
        prov.put("notifications", notifs);
        l.setProvenance(prov);

        Method m = NotificationDispatchProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Object res = m.invoke(p, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext(null, l));
        assertTrue(res instanceof Laureate);
        Laureate out = (Laureate) res;
        assertNotNull(out.getProvenance());
        assertTrue(out.getProvenance().containsKey("delivered"));
    }
}

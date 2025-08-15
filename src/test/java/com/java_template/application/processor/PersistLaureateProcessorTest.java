package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.service.EntityService;
import com.java_template.common.serializer.SerializerFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PersistLaureateProcessorTest {

    @Test
    public void testProcessSuccessful() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        EntityService entityService = mock(EntityService.class);
        // simulate no existing laureates
        when(entityService.getItems(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(JsonNodeFactory.instance.arrayNode()));
        when(entityService.addItem(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(java.util.UUID.randomUUID()));

        PersistLaureateProcessor p = new PersistLaureateProcessor(mock(SerializerFactory.class), entityService, objectMapper);

        Job job = new Job();
        job.setTechnicalId("job-1");
        job.setStatus("PERSISTING");
        ObjectNode rs = JsonNodeFactory.instance.objectNode();
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        ObjectNode l = JsonNodeFactory.instance.objectNode();
        l.put("laureateId", "L1");
        l.put("fullName", "A");
        l.put("year", 2020);
        l.put("category", "Physics");
        l.put("version", 1);
        arr.add(l);
        rs.set("toPersist", arr);
        job.setResultSummary(objectMapper.writeValueAsString(rs));

        Method m = PersistLaureateProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        Object res = m.invoke(p, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext(null, job));
        assertTrue(res instanceof Job);
        Job out = (Job) res;
        assertEquals("COMPLETED", out.getStatus());
        assertNotNull(out.getResultSummary());
        assertTrue(out.getResultSummary().contains("created"));
    }
}

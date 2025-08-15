package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.dailysummary.version_1.DailySummary;
import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class CreateNotificationProcessorTest {

    @Test
    public void testCreateNotificationSuccess() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        com.java_template.common.serializer.ProcessorSerializer ps = Mockito.mock(com.java_template.common.serializer.ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EntityService entityService = Mockito.mock(EntityService.class);
        JsonUtils jsonUtils = Mockito.mock(JsonUtils.class);

        CreateNotificationProcessor processor = new CreateNotificationProcessor(sf, entityService, jsonUtils);

        FetchJob job = new FetchJob();
        job.setRequestDate("2025-03-25");
        job.setScheduledTime("18:00Z");

        ObjectNode node = Mockito.mock(ObjectNode.class);
        DailySummary summary = new DailySummary();
        summary.setDate("2025-03-25");
        summary.setSummaryId(UUID.randomUUID().toString());
        summary.setGamesSummary("[]");

        // Mock entityService.getItemsByCondition to return an ArrayNode with one ObjectNode
        com.fasterxml.jackson.databind.node.ArrayNode arrayNode = Mockito.mock(com.fasterxml.jackson.databind.node.ArrayNode.class);
        Mockito.when(arrayNode.size()).thenReturn(1);
        Mockito.when(arrayNode.get(0)).thenReturn(node);
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> future = CompletableFuture.completedFuture(arrayNode);
        Mockito.when(entityService.getItemsByCondition(Mockito.eq(DailySummary.ENTITY_NAME), Mockito.eq(String.valueOf(DailySummary.ENTITY_VERSION)), Mockito.any(), Mockito.eq(true))).thenReturn(future);

        Mockito.when(jsonUtils.fromJsonNode(Mockito.eq(node), Mockito.eq(DailySummary.class))).thenReturn(summary);
        Mockito.when(entityService.addItem(Mockito.eq(com.java_template.application.entity.notification.version_1.Notification.ENTITY_NAME), Mockito.anyString(), Mockito.any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        java.lang.reflect.Method m = CreateNotificationProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        FetchJob out = (FetchJob) m.invoke(processor, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<>(null, job));

        assertNotNull(out);
        assertEquals("2025-03-25", out.getRequestDate());
    }
}

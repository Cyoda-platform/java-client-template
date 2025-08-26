package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.processing.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NotifySubscribersProcessorTest {

    @Test
    public void testNotifySubscribersProcessorSunnyDay() throws Exception {
        // Arrange
        ObjectMapper om = new ObjectMapper();
        JacksonProcessorSerializer ps = new JacksonProcessorSerializer(om);
        JacksonCriterionSerializer cs = new JacksonCriterionSerializer(om);
        SerializerFactory sf = new SerializerFactory(List.of(ps), List.of(cs));

        EntityService es = mock(EntityService.class);

        NotifySubscribersProcessor underTest = new NotifySubscribersProcessor(sf, es, om);

        // Build job payload that will be considered valid and in SUCCEEDED state
        ObjectNode jobNode = om.createObjectNode();
        jobNode.put("id", "job-1");
        jobNode.put("state", "SUCCEEDED");
        // include some optional fields that processor may inspect
        jobNode.put("attempts", 1);

        DataPayload payload = new DataPayload();
        payload.setData(jobNode);

        EntityProcessorCalculationRequest req = new EntityProcessorCalculationRequest();
        req.setId("id-1");
        req.setRequestId("req-1");
        req.setEntityId("job-1");
        req.setProcessorName("NotifySubscribersProcessor");
        req.setPayload(payload);

        // Build one active email subscriber node with a technicalId so updateItem is used
        ArrayNode subscribers = om.createArrayNode();
        ObjectNode sub = om.createObjectNode();
        sub.put("id", "sub-1");
        String techId = UUID.randomUUID().toString();
        sub.put("technicalId", techId);
        sub.put("active", true);
        sub.put("contact", "test@example.com");
        sub.put("type", "email");
        subscribers.add(sub);

        when(es.getItemsByCondition(
            eq("Subscriber"),
            anyString(),
            any(),
            eq(true)
        )).thenReturn(CompletableFuture.completedFuture(subscribers));

        when(es.updateItem(
            eq("Subscriber"),
            anyString(),
            any(UUID.class),
            any()
        )).thenReturn(CompletableFuture.completedFuture(UUID.fromString(techId)));

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public Object getCloudEvent() {
                return null;
            }

            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return req;
            }
        };

        // Act
        EntityProcessorCalculationResponse resp = underTest.process(ctx);

        // Assert
        assertNotNull(resp);
        assertTrue(resp.getSuccess());

        assertNotNull(resp.getPayload());
        assertNotNull(resp.getPayload().getData());
        ObjectNode out = (ObjectNode) resp.getPayload().getData();

        // Processor should set state to NOTIFIED_SUBSCRIBERS and finishedAt should be present
        assertEquals("NOTIFIED_SUBSCRIBERS", out.get("state").asText());
        assertTrue(out.hasNonNull("finishedAt"));
        // lastError should be null or absent for sunny path
        assertTrue(!out.has("lastError") || out.get("lastError").isNull());
    }
}
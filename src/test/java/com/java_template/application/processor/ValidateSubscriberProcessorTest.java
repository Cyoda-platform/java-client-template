package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.JacksonCriterionSerializer;
import com.java_template.common.serializer.JacksonProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.processing.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidateSubscriberProcessorTest {

    @Test
    void process_validEmailSubscriber_marksActive() {
        // Arrange
        ObjectMapper om = new ObjectMapper();
        JacksonProcessorSerializer ps = new JacksonProcessorSerializer(om);
        JacksonCriterionSerializer cs = new JacksonCriterionSerializer(om);
        SerializerFactory sf = new SerializerFactory(List.of(ps), List.of(cs));

        ValidateSubscriberProcessor underTest = new ValidateSubscriberProcessor(sf);

        ObjectNode data = om.createObjectNode();
        // Fields required by isValidEntity(): id, contact, type, createdAt
        data.put("id", "sub-123");
        data.put("contact", "user@example.com");
        data.put("type", "email");
        data.put("createdAt", "2025-01-01T00:00:00Z");

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest req = new EntityProcessorCalculationRequest();
        req.setId("req-1");
        req.setRequestId("request-1");
        req.setEntityId("sub-123");
        req.setProcessorName("ValidateSubscriberProcessor");
        req.setPayload(payload);

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
        assertNotNull(resp, "Response should not be null");
        assertTrue(resp.getSuccess(), "Processing should succeed for valid email subscriber");
        assertNotNull(resp.getPayload(), "Response payload should be present");
        assertNotNull(resp.getPayload().getData(), "Response payload data should be present");

        // Expect active to be true for a valid email contact
        assertTrue(resp.getPayload().getData().get("active").asBoolean(), "Subscriber should be marked active");
        // Contact should be normalized (trimmed) and type lower-cased
        assertEquals("user@example.com", resp.getPayload().getData().get("contact").asText());
        assertEquals("email", resp.getPayload().getData().get("type").asText());
    }
}
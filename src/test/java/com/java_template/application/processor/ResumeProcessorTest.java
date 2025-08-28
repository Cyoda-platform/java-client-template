package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ResumeProcessorTest {

    @Test
    void sunnyDay_resume_setsActiveTrue() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        ResumeProcessor processor = new ResumeProcessor(serializerFactory);

        // Build a minimal valid Subscriber JSON payload with active = false initially
        ObjectNode subscriberNode = objectMapper.createObjectNode();
        subscriberNode.put("subscriberId", "sub-123");
        subscriberNode.put("name", "Test Subscriber");
        subscriberNode.put("active", false);

        ArrayNode channels = objectMapper.createArrayNode();
        ObjectNode ch = objectMapper.createObjectNode();
        ch.put("address", "test@example.com");
        ch.put("type", "email");
        channels.add(ch);
        subscriberNode.set("channels", channels);

        // filters optional - omit to keep minimal

        DataPayload payload = new DataPayload();
        payload.setData(subscriberNode);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ResumeProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() {
                return null;
            }

            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return request;
            }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(ctx);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "Output data should not be null");

        // active should be true after resume
        assertTrue(out.has("active") && out.get("active").asBoolean(), "Subscriber.active should be true after resume");

        // ensure subscriberId and name unchanged
        assertEquals("sub-123", out.get("subscriberId").asText(), "subscriberId should be preserved");
        assertEquals("Test Subscriber", out.get("name").asText(), "name should be preserved");
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DeleteProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        DeleteProcessor processor = new DeleteProcessor(serializerFactory);

        // Build a valid Subscriber payload according to Subscriber.isValid()
        ObjectNode data = objectMapper.createObjectNode();
        data.put("subscriberId", "sub-123");
        data.put("name", "Test Subscriber");
        data.put("active", true);

        ArrayNode channels = objectMapper.createArrayNode();
        ObjectNode channel = objectMapper.createObjectNode();
        channel.put("address", "test@example.com");
        channel.put("type", "email");
        channels.add(channel);
        data.set("channels", channels);

        // filters are optional; omit for this sunny path

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("DeleteProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);

        // active should be set to false by DeleteProcessor
        assertTrue(out.has("active"));
        assertFalse(out.get("active").asBoolean());

        // channels should be cleared (empty array)
        assertTrue(out.has("channels"));
        JsonNode outChannels = out.get("channels");
        assertTrue(outChannels.isArray());
        assertEquals(0, outChannels.size());

        // lastNotifiedAt should be set (non-null, non-blank string)
        assertTrue(out.has("lastNotifiedAt"));
        String lastNotifiedAt = out.get("lastNotifiedAt").asText();
        assertNotNull(lastNotifiedAt);
        assertFalse(lastNotifiedAt.isBlank());
    }
}
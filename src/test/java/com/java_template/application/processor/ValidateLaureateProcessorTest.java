package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

public class ValidateLaureateProcessorTest {

    @Test
    void sunnyDay_validateLaureateProcessor_process() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        ValidateLaureateProcessor processor = new ValidateLaureateProcessor(serializerFactory);

        ObjectNode provenance = objectMapper.createObjectNode();
        provenance.put("ingestionJobId", "ingest-123");
        provenance.put("sourceRecordId", "src-456");
        provenance.put("sourceTimestamp", "2025-01-01T00:00:00Z");

        ObjectNode data = objectMapper.createObjectNode();
        data.put("laureateId", "L-001");
        data.put("awardYear", "2024");
        data.put("category", "physics");
        data.set("provenance", provenance);
        // ensure validationErrors is present so processEntityLogic can clear it
        data.set("validationErrors", objectMapper.createArrayNode());

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ValidateLaureateProcessor");
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
        assertNotNull(response);
        assertTrue(response.getSuccess());

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertTrue(out.has("processingStatus"));
        assertEquals("VALIDATED", out.get("processingStatus").asText());
        assertTrue(out.has("validationErrors"));
        assertTrue(out.get("validationErrors").isArray());
        assertEquals(0, out.get("validationErrors").size());
    }
}
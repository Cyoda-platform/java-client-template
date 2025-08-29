package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LinkResourcesProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - ObjectMapper and real serializers
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked (constructor requires it); processor does not call it in sunny path
        EntityService entityService = mock(EntityService.class);

        LinkResourcesProcessor processor = new LinkResourcesProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Report JSON payload that will pass validation and has status COMPLETED,
        // missing visualizationUrl so processor will create one.
        String reportId = "rpt-1234";
        ObjectNode reportJson = objectMapper.createObjectNode();
        reportJson.put("name", "Monthly Report");
        reportJson.put("reportId", reportId);
        reportJson.put("jobTechnicalId", UUID.randomUUID().toString());
        reportJson.put("status", "COMPLETED");
        reportJson.put("generatedAt", "2025-08-01T00:00:00Z");
        reportJson.put("createdBy", "tester");
        // intentionally omit visualizationUrl to trigger creation
        // omit bookingsSample to avoid strict persistedAt validation

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("LinkResourcesProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(reportJson);
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

        assertNotNull(response.getPayload());
        JsonNode data = response.getPayload().getData();
        assertNotNull(data);

        // Status should be transitioned to AVAILABLE
        assertEquals("AVAILABLE", data.get("status").asText());

        // visualizationUrl should have been created from reportId
        String expectedViz = "/artifacts/" + reportId + "/chart.png";
        assertEquals(expectedViz, data.get("visualizationUrl").asText());
    }
}
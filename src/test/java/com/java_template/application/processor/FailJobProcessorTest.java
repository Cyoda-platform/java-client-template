package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
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

public class FailJobProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - ObjectMapper and real serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        FailJobProcessor processor = new FailJobProcessor(serializerFactory);

        // Build a valid WeeklySendJob that passes isValid()
        WeeklySendJob job = new WeeklySendJob();
        job.setCatFactTechnicalId("technical-id-123");
        job.setCreatedAt("2025-01-01T00:00:00Z");
        job.setRunAt("2025-01-01T00:05:00Z"); // initial runAt (processor will overwrite)
        job.setScheduledFor("2025-01-02T00:00:00Z");
        job.setStatus("PENDING");
        job.setErrorMessage(null);

        JsonNode entityJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("FailJobProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        DataPayload responsePayload = response.getPayload();
        assertNotNull(responsePayload, "Response payload should not be null");
        JsonNode responseData = responsePayload.getData();
        assertNotNull(responseData, "Response data should not be null");

        // Core happy-path expectations: status set to FAILED, errorMessage populated, runAt updated
        assertEquals("FAILED", responseData.get("status").asText(), "Status should be set to FAILED");
        assertTrue(responseData.hasNonNull("errorMessage"), "errorMessage should be present");
        String errorMsg = responseData.get("errorMessage").asText();
        assertTrue(errorMsg.contains("Job failed"), "errorMessage should mention job failure");

        String runAt = responseData.get("runAt").asText();
        assertNotNull(runAt);
        assertFalse(runAt.isBlank(), "runAt should be set by the processor");
    }
}
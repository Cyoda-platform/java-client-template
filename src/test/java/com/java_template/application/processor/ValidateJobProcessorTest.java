package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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

import static org.junit.jupiter.api.Assertions.*;

public class ValidateJobProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real Jackson setup
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        ValidateJobProcessor processor = new ValidateJobProcessor(serializerFactory);

        // Build a valid IngestionJob that will pass isValid() and will be skipped by processor
        // because its status is NOT "PENDING" (this avoids making external HTTP calls).
        IngestionJob job = new IngestionJob();
        job.setRequestedBy("user-123");
        job.setSourceUrl("http://example.com/resource"); // present but will not be used for non-PENDING
        job.setStartedAt("2020-01-01T00:00:00Z");
        job.setStatus("COMPLETED"); // non-PENDING => processor should return entity unchanged

        // Ensure entity is valid per IngestionJob.isValid()
        assertTrue(job.isValid(), "Test entity must be valid");

        JsonNode entityJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ValidateJobProcessor");
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
        assertNotNull(response);
        assertTrue(response.getSuccess(), "Processor should report success for sunny path");

        // Inspect returned payload data for expected state (entity unchanged, status still COMPLETED)
        assertNotNull(response.getPayload(), "Response payload must be present");
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData, "Returned data must be present");

        // Check status preserved
        assertEquals("COMPLETED", returnedData.get("status").asText(), "Status should remain COMPLETED");

        // Check startedAt preserved
        assertEquals("2020-01-01T00:00:00Z", returnedData.get("startedAt").asText(), "startedAt should remain unchanged");
    }
}
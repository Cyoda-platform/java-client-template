package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.petimportjob.version_1.PetImportJob;
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

public class StartImportProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        StartImportProcessor processor = new StartImportProcessor(serializerFactory);

        // Prepare a PetImportJob that satisfies isValid() but needs initialization by the processor
        PetImportJob job = new PetImportJob();
        job.setRequestId("req-123");
        job.setRequestedAt("2025-01-01T00:00:00Z");
        job.setSourceUrl("http://example.com/import.csv");
        job.setStatus("PENDING"); // sunny path: should transition to VALIDATING
        job.setImportedCount(null); // should be initialized to 0 by processor
        job.setErrors(null); // should be set to empty string by processor

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("StartImportProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(jobJson);
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
        assertTrue(response.getSuccess(), "Processing should succeed on sunny path");

        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response data should not be null");

        // Verify core state changes performed by StartImportProcessor on sunny path
        assertEquals("VALIDATING", responseData.get("status").asText(), "Status should transition to VALIDATING");
        assertEquals(0, responseData.get("importedCount").asInt(), "importedCount should be initialized to 0");
        assertEquals("", responseData.get("errors").asText(), "errors should be cleared to empty string when null");
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class JobValidationProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real ObjectMapper configured as required
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = Mockito.mock(EntityService.class);

        // Prepare a valid Owner payload to be returned by EntityService.getItem(...)
        DataPayload ownerPayload = new DataPayload();
        // Owner minimal JSON { "id": "...", "name": "Owner Name" }
        UUID ownerUuid = UUID.randomUUID();
        JsonNode ownerJson = objectMapper.createObjectNode()
                .put("id", ownerUuid.toString())
                .put("name", "Owner Name");
        ownerPayload.setData(ownerJson);

        when(entityService.getItem(any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(ownerPayload));

        // Instantiate processor with real serializers and mocked entityService
        JobValidationProcessor processor = new JobValidationProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid AdoptionJob entity that passes isValid()
        AdoptionJob job = new AdoptionJob();
        job.setId(UUID.randomUUID().toString());
        job.setOwnerId(ownerUuid.toString());
        job.setCriteria("{}"); // valid JSON
        job.setStatus("PENDING"); // will be advanced to RUNNING on success
        job.setCreatedAt(Instant.now().toString());
        job.setResultCount(5); // any non-negative
        job.getResultsPreview().clear(); // empty preview is valid

        // Convert entity to JsonNode for payload
        JsonNode jobJson = objectMapper.valueToTree(job);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(job.getId());
        request.setProcessorName("JobValidationProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(jobJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect payload data for expected sunny-day changes
        assertNotNull(response.getPayload());
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData);

        // After successful validation processor should set status to RUNNING,
        // reset resultCount to 0 and resultsPreview to empty list
        assertEquals("RUNNING", responseData.get("status").asText());
        assertEquals(0, responseData.get("resultCount").asInt());
        JsonNode previewNode = responseData.get("resultsPreview");
        assertNotNull(previewNode);
        assertTrue(previewNode.isArray());
        assertEquals(0, previewNode.size());
    }
}
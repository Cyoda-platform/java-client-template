package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import com.java_template.common.workflow.CyodaEventContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class StartMatchingProcessorTest {

    @Test
    void sunnyDay_startMatchingProcessor_process_setsRunningAndInitializesPreview() throws Exception {
        // Arrange - ObjectMapper and serializers (real, no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure to ignore unknown properties
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of((ProcessorSerializer) processorSerializer),
                java.util.List.of((CriterionSerializer) criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a valid Owner to be returned by EntityService
        UUID ownerUuid = UUID.randomUUID();
        Owner owner = new Owner();
        owner.setId(ownerUuid.toString());
        owner.setName("Test Owner");
        // contactEmail optional - leave null

        DataPayload ownerPayload = new DataPayload();
        ownerPayload.setData(objectMapper.valueToTree(owner));

        // Stub entityService.getItem(...) to return the owner payload
        when(entityService.getItem(eq(Owner.ENTITY_NAME), eq(Owner.ENTITY_VERSION), eq(ownerUuid)))
                .thenReturn(CompletableFuture.completedFuture(ownerPayload));

        // Create processor instance (real)
        StartMatchingProcessor processor = new StartMatchingProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid AdoptionJob JSON payload that passes isValid()
        AdoptionJob job = new AdoptionJob();
        job.setId(UUID.randomUUID().toString());
        job.setOwnerId(ownerUuid.toString());
        job.setCreatedAt("2025-01-01T00:00:00Z");
        job.setStatus("NEW");
        job.setCriteria("{\"dummy\":\"value\"}");
        // resultCount defaults to 0; resultsPreview defaults to empty list

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(job.getId());
        request.setProcessorName("StartMatchingProcessor");
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

        // Assert basic response success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Deserialize returned payload back to AdoptionJob and assert sunny-day changes
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());
        AdoptionJob resultJob = objectMapper.treeToValue(response.getPayload().getData(), AdoptionJob.class);
        assertNotNull(resultJob);
        assertEquals("RUNNING", resultJob.getStatus(), "Processor should set status to RUNNING");
        assertNotNull(resultJob.getResultsPreview(), "Processor should initialize resultsPreview");
        assertEquals(0, resultJob.getResultCount().intValue(), "Processor should initialize resultCount to 0");

        // Verify EntityService was invoked to fetch the owner
        verify(entityService, atLeastOnce()).getItem(eq(Owner.ENTITY_NAME), eq(Owner.ENTITY_VERSION), eq(ownerUuid));
    }
}
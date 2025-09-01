package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ScheduleSendProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a CatFact that passes isValid() and has validationStatus = "VALID"
        CatFact catFact = new CatFact();
        String catFactId = UUID.randomUUID().toString();
        catFact.setTechnicalId(catFactId);
        catFact.setText("Cats are awesome");
        catFact.setSource("catfact.ninja");
        catFact.setFetchedAt("2025-09-07T09:00:01Z");
        catFact.setValidationStatus("VALID");
        catFact.setSendCount(0);
        catFact.setEngagementScore(1.5);

        // Prepare a WeeklySendJob returned by EntityService.getItemsByCondition
        WeeklySendJob existingJob = new WeeklySendJob();
        // existing job may not have catFactTechnicalId yet (null or blank), that's fine
        existingJob.setCatFactTechnicalId(""); // empty so processor will set it
        existingJob.setCreatedAt("2025-09-01T00:00:00Z");
        existingJob.setRunAt(""); // blank so processor will populate runAt
        existingJob.setScheduledFor("2025-09-08T00:00:00Z");
        existingJob.setStatus("RUNNING");
        existingJob.setErrorMessage(null);

        // Build DataPayload for the job: data is job JSON, meta contains entityId
        DataPayload jobPayload = new DataPayload();
        jobPayload.setData(objectMapper.valueToTree(existingJob));
        String jobTechnicalId = UUID.randomUUID().toString();
        jobPayload.setMeta(objectMapper.createObjectNode().put("entityId", jobTechnicalId));

        // Stub EntityService.getItemsByCondition to return the running job
        when(entityService.getItemsByCondition(
                eq(WeeklySendJob.ENTITY_NAME),
                eq(WeeklySendJob.ENTITY_VERSION),
                any(),
                eq(true)
        )).thenReturn(CompletableFuture.completedFuture(List.of(jobPayload)));

        // Stub EntityService.updateItem to succeed when updating the WeeklySendJob
        when(entityService.updateItem(eq(UUID.fromString(jobTechnicalId)), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(jobTechnicalId)));

        // Instantiate processor with real serializerFactory, mocked entityService and real objectMapper
        ScheduleSendProcessor processor = new ScheduleSendProcessor(serializerFactory, entityService, objectMapper);

        // Build request with CatFact payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ScheduleSendProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(catFact));
        request.setPayload(payload);

        // Minimal context
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic successful response
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Response payload should contain CatFact JSON; verify validationStatus remains VALID and technicalId preserved
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());
        assertEquals(catFactId, response.getPayload().getData().get("technicalId").asText());
        assertEquals("VALID", response.getPayload().getData().get("validationStatus").asText());

        // Verify that the processor attempted to attach the CatFact to the WeeklySendJob by calling updateItem
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(jobTechnicalId)), argThat(arg -> {
            // arg is the WeeklySendJob object passed to updateItem; convert via ObjectMapper for inspection
            try {
                // convert arg to WeeklySendJob to inspect fields
                WeeklySendJob updated = objectMapper.convertValue(arg, WeeklySendJob.class);
                return catFactId.equals(updated.getCatFactTechnicalId()) && updated.getRunAt() != null && !updated.getRunAt().isBlank();
            } catch (Exception e) {
                return false;
            }
        }));
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CompleteJobProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real ObjectMapper per instructions
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory (no Spring)
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a valid CatFact that the processor will retrieve
        CatFact catFact = new CatFact();
        catFact.setTechnicalId(UUID.randomUUID().toString());
        catFact.setText("Cats are great.");
        catFact.setSource("catfact.ninja");
        catFact.setFetchedAt("2025-09-01T00:00:00Z");
        catFact.setSendCount(0);
        catFact.setEngagementScore(1.0);
        catFact.setValidationStatus("VALID");

        DataPayload catFactPayload = new DataPayload();
        catFactPayload.setData(objectMapper.valueToTree(catFact));

        // Stub EntityService.getItem to return the CatFact payload
        when(entityService.getItem(any())).thenReturn(CompletableFuture.completedFuture(catFactPayload));

        // Create the processor (use real serializerFactory, mocked entityService, and real objectMapper)
        CompleteJobProcessor processor = new CompleteJobProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid WeeklySendJob JSON payload (must pass isValid())
        WeeklySendJob job = new WeeklySendJob();
        // Provide a valid UUID reference to the CatFact
        String catFactId = UUID.randomUUID().toString();
        job.setCatFactTechnicalId(catFactId);
        job.setCreatedAt("2025-09-01T00:00:00Z");
        job.setRunAt("2025-09-01T00:00:00Z"); // required by isValid()
        job.setScheduledFor("2025-09-01T00:00:00Z");
        job.setStatus("PENDING");
        job.setErrorMessage(null);

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("CompleteJobProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(jobJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Execute
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assertions: response exists and indicates success, and status updated to COMPLETED
        assertNotNull(response);
        assertTrue(response.getSuccess());

        DataPayload responsePayload = response.getPayload();
        assertNotNull(responsePayload);
        JsonNode responseData = responsePayload.getData();
        assertNotNull(responseData);

        assertEquals("COMPLETED", responseData.get("status").asText());
        // errorMessage was set to null on success; allow either null node or missing
        JsonNode errorNode = responseData.get("errorMessage");
        assertTrue(errorNode == null || errorNode.isNull());
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
import com.java_template.application.entity.owner.version_1.Owner;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class NotifyResultsProcessorTest {

    @Test
    void sunnyDay_notify_results_sets_status_notified() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare owner that will be returned by entityService.getItem(...)
        Owner owner = new Owner();
        owner.setId(UUID.randomUUID().toString());
        owner.setName("Jane Doe");
        owner.setContactEmail("jane.doe@example.com");
        DataPayload ownerPayload = new DataPayload();
        ownerPayload.setData(objectMapper.valueToTree(owner));
        when(entityService.getItem(any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(ownerPayload));

        // Instantiate processor (no Spring)
        NotifyResultsProcessor processor = new NotifyResultsProcessor(serializerFactory, entityService, objectMapper);

        // Prepare AdoptionJob entity JSON that passes isValid() and has status COMPLETED
        AdoptionJob job = new AdoptionJob();
        String ownerId = owner.getId(); // use same owner id
        job.setId(UUID.randomUUID().toString());
        job.setOwnerId(ownerId);
        job.setCreatedAt("2025-01-01T00:00:00Z");
        job.setStatus("COMPLETED"); // triggers notification path
        job.setCriteria("{}");
        job.setResultCount(1);
        job.getResultsPreview().add("result-1");

        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(job));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(job.getId());
        request.setProcessorName("NotifyResultsProcessor");
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

        // payload data should reflect the job status change to NOTIFIED
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());
        AdoptionJob processedJob = objectMapper.treeToValue(response.getPayload().getData(), AdoptionJob.class);
        assertNotNull(processedJob);
        assertEquals("NOTIFIED", processedJob.getStatus());

        // verify entityService.getItem was called to fetch owner
        verify(entityService, atLeastOnce()).getItem(any(UUID.class));
    }
}
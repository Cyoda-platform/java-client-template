package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.pet.version_1.Pet;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NotifyOwnerProcessorTest {

    @Test
    void sunnyDay_notifyOwner_verified_owner_creates_completed_job() throws Exception {
        // Setup real ObjectMapper as required
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory (no Spring)
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService may be mocked
        EntityService entityService = mock(EntityService.class);

        // Prepare a Pet that will be returned when the processor attempts to fetch pet details
        Pet pet = new Pet();
        pet.setId(UUID.randomUUID().toString());
        pet.setName("Fluffy");
        pet.setSpecies("Cat");
        pet.setStatus("AVAILABLE");
        JsonNode petJson = objectMapper.valueToTree(pet);
        DataPayload petPayload = new DataPayload();
        petPayload.setData(petJson);

        // Stub entityService.getItem(...) to return the pet payload
        when(entityService.getItem(any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(petPayload));

        // Stub entityService.addItem(...) to simulate creating the IngestionJob
        when(entityService.addItem(eq(IngestionJob.ENTITY_NAME), eq(IngestionJob.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Create the processor instance (real serializerFactory, mocked entityService, real objectMapper)
        NotifyOwnerProcessor processor = new NotifyOwnerProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Owner entity JSON for the request (must satisfy Owner.isValid())
        Owner owner = new Owner();
        owner.setId(UUID.randomUUID().toString());
        owner.setName("Jane Doe");
        owner.setEmail("jane.doe@example.com");
        owner.setVerified(Boolean.TRUE); // verified -> completed job path
        owner.setPetsOwned(List.of(pet.getId()));

        JsonNode ownerJson = objectMapper.valueToTree(owner);

        // Build the request payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(owner.getId());
        request.setProcessorName("NotifyOwnerProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(ownerJson);
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

        // Assert basic response success
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processor should report success on sunny path");

        // Inspect returned payload contains owner data and verified remains true
        assertNotNull(response.getPayload(), "Response payload must be present");
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData, "Returned payload data must be present");
        assertTrue(returnedData.get("verified").asBoolean(), "Returned owner should remain verified");

        // Verify that processor attempted to fetch the pet and created an ingestion job
        verify(entityService, atLeastOnce()).getItem(any(UUID.class));
        verify(entityService, atLeastOnce()).addItem(eq(IngestionJob.ENTITY_NAME), eq(IngestionJob.ENTITY_VERSION), any());
    }
}
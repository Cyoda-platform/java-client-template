package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
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

public class MatchingProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare an Owner that matches preferences
        UUID ownerUuid = UUID.randomUUID();
        Owner owner = new Owner();
        owner.setId(ownerUuid.toString());
        owner.setName("Jane Doe");
        // preferences JSON: wants a Dog, Labrador, age between 1 and 5
        owner.setPreferences("{\"species\":\"Dog\",\"breed\":\"Labrador\",\"ageMax\":5,\"ageMin\":1}");

        JsonNode ownerJson = objectMapper.valueToTree(owner);
        DataPayload ownerPayload = new DataPayload();
        ownerPayload.setData(ownerJson);

        // Stub entityService.getItem(...) to return the owner payload
        when(entityService.getItem(eq(ownerUuid)))
                .thenReturn(CompletableFuture.completedFuture(ownerPayload));

        // Prepare pets - two matching AVAILABLE pets
        Pet pet1 = new Pet();
        pet1.setId(UUID.randomUUID().toString());
        pet1.setName("Buddy");
        pet1.setSpecies("Dog");
        pet1.setBreed("Labrador");
        pet1.setStatus("AVAILABLE");
        pet1.setAge(3);

        Pet pet2 = new Pet();
        pet2.setId(UUID.randomUUID().toString());
        pet2.setName("Rex");
        pet2.setSpecies("Dog");
        pet2.setBreed("Labrador");
        pet2.setStatus("AVAILABLE");
        pet2.setAge(2);

        DataPayload petPayload1 = new DataPayload();
        petPayload1.setData(objectMapper.valueToTree(pet1));
        DataPayload petPayload2 = new DataPayload();
        petPayload2.setData(objectMapper.valueToTree(pet2));

        // Stub entityService.getItems(...) to return the list of pets
        when(entityService.getItems(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), isNull(), isNull(), isNull()))
                .thenReturn(CompletableFuture.completedFuture(List.of(petPayload1, petPayload2)));

        // Instantiate processor with real serializerFactory and mocked entityService
        MatchingProcessor processor = new MatchingProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid AdoptionJob request payload that passes isValid()
        AdoptionJob job = new AdoptionJob();
        job.setId(UUID.randomUUID().toString());
        job.setOwnerId(ownerUuid.toString());
        job.setCriteria("{}"); // minimal non-blank
        job.setCreatedAt("2025-01-01T00:00:00Z");
        job.setStatus("PENDING");
        job.setResultCount(0);
        job.setResultsPreview(List.of()); // empty list initially

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(job.getId());
        request.setProcessorName("MatchingProcessor");
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
        assertTrue(response.getSuccess(), "Processing should succeed in sunny-day path");
        assertNotNull(response.getPayload(), "Response payload should not be null");
        assertNotNull(response.getPayload().getData(), "Response payload data should not be null");

        // Convert returned payload to AdoptionJob and verify expected updates
        AdoptionJob resultJob = objectMapper.treeToValue(response.getPayload().getData(), AdoptionJob.class);
        assertNotNull(resultJob, "Resulting AdoptionJob should be deserializable");
        assertEquals("COMPLETED", resultJob.getStatus(), "Job should be marked COMPLETED on sunny path");
        // Expect two matching pets
        assertEquals(2, resultJob.getResultCount(), "Result count should reflect number of matching pets");
        assertNotNull(resultJob.getResultsPreview(), "Results preview should be present");
        assertEquals(2, resultJob.getResultsPreview().size(), "Results preview should contain two ids");
    }
}
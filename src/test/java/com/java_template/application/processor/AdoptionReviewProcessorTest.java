package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.criterion.CriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.service.EntityService;
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

public class AdoptionReviewProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock EntityService (only mocked dependency allowed)
        EntityService entityService = mock(EntityService.class);

        // Prepare a Pet payload that will be returned by getItemsByCondition.
        Pet storedPet = new Pet();
        storedPet.setId("PET-1");
        storedPet.setName("Buddy");
        storedPet.setAge(3);
        storedPet.setBreed("Mixed");
        storedPet.setSpecies("dog");
        storedPet.setSex("male");
        storedPet.setStatus("available");
        // Convert pet to JSON node and add a technicalId so processor will attempt update
        ObjectNode petNode = (ObjectNode) objectMapper.valueToTree(storedPet);
        String technicalId = UUID.randomUUID().toString();
        petNode.put("technicalId", technicalId);

        DataPayload petDataPayload = new DataPayload();
        petDataPayload.setData(petNode);

        when(entityService.getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(petDataPayload)));

        when(entityService.updateItem(eq(UUID.fromString(technicalId)), any(Pet.class)))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(technicalId)));

        // Construct processor
        AdoptionReviewProcessor processor = new AdoptionReviewProcessor(serializerFactory, entityService, objectMapper);

        // Prepare a valid AdoptionRequest that represents an "approved" decision (sunny path)
        AdoptionRequest requestEntity = new AdoptionRequest();
        requestEntity.setId("REQ-1");
        requestEntity.setPetId("PET-1"); // business id used to search pet
        requestEntity.setRequesterId("USER-1");
        requestEntity.setStatus("approved");
        requestEntity.setSubmittedAt("2020-01-01T00:00:00Z");

        // Build request payload
        DataPayload requestPayload = new DataPayload();
        requestPayload.setData(objectMapper.valueToTree(requestEntity));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("AdoptionReviewProcessor");
        request.setPayload(requestPayload);

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

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // processedAt should have been set
        assertTrue(out.hasNonNull("processedAt"));
        // processedBy should be set to manual-review by processor when none provided
        assertTrue(out.hasNonNull("processedBy"));
        assertEquals("manual-review", out.get("processedBy").asText());

        // Verify entityService was used to fetch and update the Pet
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(technicalId)), any(Pet.class));
    }
}
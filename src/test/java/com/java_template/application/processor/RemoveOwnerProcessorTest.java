package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RemoveOwnerProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare an adoption request payload that will be returned by entityService.getItemsByCondition
        AdoptionRequest ar = new AdoptionRequest();
        String arRequestId = UUID.randomUUID().toString();
        ar.setRequestId(arRequestId);
        ar.setPetId("pet-1");
        ar.setRequesterId("owner-1");
        ar.setStatus("submitted");
        ar.setSubmittedAt("2025-01-01T00:00:00Z");

        DataPayload arPayload = new DataPayload();
        // set data as JsonNode so objectMapper.treeToValue works
        JsonNode arNode = objectMapper.valueToTree(ar);
        arPayload.setData(arNode);
        // set technical id so updateItem is attempted
        UUID technicalId = UUID.randomUUID();
        arPayload.setId(technicalId.toString());

        when(entityService.getItemsByCondition(anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(List.of(arPayload)));

        // Stub updateItem to succeed
        when(entityService.updateItem(any(UUID.class), any())).thenReturn(CompletableFuture.completedFuture(null));

        // Build the processor under test
        RemoveOwnerProcessor processor = new RemoveOwnerProcessor(serializerFactory, entityService, objectMapper);

        // Create an Owner entity that is valid and has fields that should be cleared/changed
        Owner owner = new Owner();
        owner.setOwnerId("owner-1");
        owner.setName("John Doe");
        owner.setContactEmail("john.doe@example.com");
        owner.setContactPhone("1234567890");
        owner.setVerificationStatus("active");
        owner.setSavedPets(List.of("pet-1"));
        owner.setAdoptedPets(List.of("pet-2"));

        JsonNode ownerNode = objectMapper.valueToTree(owner);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("RemoveOwnerProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(ownerNode);
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

        Object respData = response.getPayload().getData();
        assertNotNull(respData);
        JsonNode outNode = (JsonNode) respData;

        // verificationStatus should be set to "removed"
        assertEquals("removed", outNode.get("verificationStatus").asText());

        // contactEmail and contactPhone should be cleared (null or missing)
        assertTrue(outNode.get("contactEmail") == null || outNode.get("contactEmail").isNull());
        assertTrue(outNode.get("contactPhone") == null || outNode.get("contactPhone").isNull());

        // savedPets and adoptedPets should be empty arrays
        JsonNode savedPetsNode = outNode.get("savedPets");
        assertNotNull(savedPetsNode);
        assertTrue(savedPetsNode.isArray());
        assertEquals(0, savedPetsNode.size());

        JsonNode adoptedPetsNode = outNode.get("adoptedPets");
        assertNotNull(adoptedPetsNode);
        assertTrue(adoptedPetsNode.isArray());
        assertEquals(0, adoptedPetsNode.size());

        // Verify that getItemsByCondition and updateItem were invoked as part of sunny path
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(AdoptionRequest.ENTITY_NAME), eq(AdoptionRequest.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).updateItem(eq(technicalId), any());
    }
}
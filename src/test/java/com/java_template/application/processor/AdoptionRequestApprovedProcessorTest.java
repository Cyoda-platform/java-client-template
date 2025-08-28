package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AdoptionRequestApprovedProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - serializers and factory (real objects)
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare pet that is AVAILABLE so processor will update it to PENDING_ADOPTION
        UUID petUuid = UUID.randomUUID();
        Pet pet = new Pet();
        pet.setId(petUuid.toString());
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("AVAILABLE");
        // ensure pet validity
        // id, name, species, status are set above

        JsonNode petJson = objectMapper.valueToTree(pet);
        DataPayload petPayload = new DataPayload();
        petPayload.setData(petJson);

        // Stub getItem to return the pet payload for the expected UUID
        when(entityService.getItem(eq(petUuid)))
                .thenReturn(CompletableFuture.completedFuture(petPayload));
        // Stub updateItem to return a completed future with the UUID
        when(entityService.updateItem(eq(petUuid), any()))
                .thenReturn(CompletableFuture.completedFuture(petUuid));

        // Instantiate processor under test
        AdoptionRequestApprovedProcessor processor = new AdoptionRequestApprovedProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid AdoptionRequest that passes isValid()
        AdoptionRequest reqEntity = new AdoptionRequest();
        reqEntity.setId("ar-1");
        reqEntity.setPetId(petUuid.toString());
        reqEntity.setRequesterName("Jane Doe");
        reqEntity.setContactEmail("jane@example.com");
        reqEntity.setContactPhone("555-0100");
        reqEntity.setStatus("CREATED"); // non-blank required
        reqEntity.setSubmittedAt("2020-01-01T00:00:00Z");
        // other optional fields can be null

        JsonNode reqJson = objectMapper.valueToTree(reqEntity);
        DataPayload requestPayload = new DataPayload();
        requestPayload.setData(reqJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("AdoptionRequestApprovedProcessor");
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

        // Inspect returned payload - should reflect approved request and notes about pet update
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        JsonNode out = response.getPayload().getData();
        // Convert back to AdoptionRequest to assert fields
        AdoptionRequest outEntity = objectMapper.treeToValue(out, AdoptionRequest.class);
        assertNotNull(outEntity);
        assertEquals("APPROVED", outEntity.getStatus());
        assertNotNull(outEntity.getNotes());
        assertTrue(outEntity.getNotes().contains("PENDING_ADOPTION") || outEntity.getNotes().contains("Pet status set"));
        // Verify entityService interactions occurred
        verify(entityService, atLeastOnce()).getItem(eq(petUuid));
        verify(entityService, atLeastOnce()).updateItem(eq(petUuid), any());
    }
}
```java
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.user.version_1.User;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class AdoptionRequestProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        EntityService entityService = mock(EntityService.class);

        // Mock return values for the pet and user data
        Pet pet = new Pet();
        pet.setId("pet1");
        pet.setName("Buddy");
        pet.setAge(3);
        pet.setType("dog");
        pet.setStatus("available");

        User user = new User();
        user.setId("user1");
        user.setName("John Doe");
        user.setEmail("john.doe@example.com");

        when(entityService.getItem(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(new DataPayload() {{
                    setData(objectMapper.valueToTree(pet));
                }}));

        when(entityService.getItem(eq(User.ENTITY_NAME), eq(User.ENTITY_VERSION), any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(new DataPayload() {{
                    setData(objectMapper.valueToTree(user));
                }}));

        AdoptionRequestProcessor processor = new AdoptionRequestProcessor(serializerFactory, entityService, objectMapper);

        // Create a valid AdoptionRequest
        AdoptionRequest requestEntity = new AdoptionRequest();
        requestEntity.setId("req1");
        requestEntity.setPetId("pet1");
        requestEntity.setUserId("user1");
        requestEntity.setStatus("pending");

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("AdoptionRequestProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(requestEntity));
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
        assertEquals("approved", requestEntity.getStatus());
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString("pet1")), any(Pet.class));
    }
}
```
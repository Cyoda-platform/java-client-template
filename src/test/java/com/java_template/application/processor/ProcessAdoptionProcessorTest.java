```java
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoption.version_1.Adoption;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ProcessAdoptionProcessorTest {

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
        
        // Mocking pet and user retrieval
        Pet pet = new Pet();
        pet.setId("petId");
        pet.setName("Fluffy");
        pet.setBreed("Persian");
        pet.setAge(2);
        pet.setType("Cat");
        pet.setStatus("available");

        User user = new User();
        user.setId("userId");
        user.setName("John Doe");
        user.setEmail("john.doe@example.com");
        user.setPhone("123-456-7890");

        when(entityService.getItem(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(new DataPayload() {{
                    setData(objectMapper.valueToTree(pet));
                }}));
        
        when(entityService.getItem(eq(User.ENTITY_NAME), eq(User.ENTITY_VERSION), any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(new DataPayload() {{
                    setData(objectMapper.valueToTree(user));
                }}));
        
        ProcessAdoptionProcessor processor = new ProcessAdoptionProcessor(serializerFactory, entityService, objectMapper);

        Adoption adoption = new Adoption();
        adoption.setId("adoptionId");
        adoption.setPetId("petId");
        adoption.setUserId("userId");
        adoption.setStatus("PENDING");

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("requestId");
        request.setRequestId("requestId");
        request.setEntityId("adoptionId");
        request.setProcessorName("ProcessAdoptionProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(adoption));
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
        assertEquals("COMPLETED", adoption.getStatus());
        
        // Verify EntityService was called to retrieve pet and user
        verify(entityService, times(1)).getItem(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), eq(UUID.fromString("petId")));
        verify(entityService, times(1)).getItem(eq(User.ENTITY_NAME), eq(User.ENTITY_VERSION), eq(UUID.fromString("userId")));
    }
}
```
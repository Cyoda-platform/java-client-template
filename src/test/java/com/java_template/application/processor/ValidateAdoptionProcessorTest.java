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

public class ValidateAdoptionProcessorTest {

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
        
        User user = new User();
        user.setId("user1");
        user.setName("John Doe");
        user.setEmail("john.doe@example.com");
        user.setPhone("123-456-7890");

        Pet pet = new Pet();
        pet.setId("pet1");
        pet.setName("Fluffy");
        pet.setBreed("Persian");
        pet.setAge(2);
        pet.setType("Cat");
        pet.setStatus("available");

        when(entityService.getItem(eq(User.ENTITY_NAME), eq(User.ENTITY_VERSION), any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(new DataPayload() {{
                    setData(objectMapper.valueToTree(user));
                }}));
        
        when(entityService.getItem(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(new DataPayload() {{
                    setData(objectMapper.valueToTree(pet));
                }}));
        
        ValidateAdoptionProcessor processor = new ValidateAdoptionProcessor(serializerFactory, entityService);

        Adoption adoption = new Adoption();
        adoption.setId("adoption1");
        adoption.setUserId("user1");
        adoption.setPetId("pet1");
        adoption.setStatus("pending");

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("request1");
        request.setRequestId("request1");
        request.setEntityId("adoption1");
        request.setProcessorName("ValidateAdoptionProcessor");
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
        assertEquals("validated", ((Adoption) response.getPayload().getData().traverse(Adoption.class)).getStatus());
        
        // Verify EntityService was called for User and Pet retrieval
        verify(entityService, times(1)).getItem(eq(User.ENTITY_NAME), eq(User.ENTITY_VERSION), any(UUID.class));
        verify(entityService, times(1)).getItem(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(UUID.class));
    }
}
```
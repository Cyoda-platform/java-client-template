```java
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoption.version_1.Adoption;
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
import static org.mockito.ArgumentMatchers.*;
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
        Adoption adoptionEntity = new Adoption();
        adoptionEntity.setId("adopt1");
        adoptionEntity.setPetId("pet1");
        adoptionEntity.setUserId("user1");
        adoptionEntity.setStatus("PENDING_ADOPTION");

        Pet petEntity = new Pet();
        petEntity.setId("pet1");
        petEntity.setStatus("available");

        when(entityService.getItem(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(new DataPayload() {{
                    setData(objectMapper.valueToTree(petEntity));
                }}));

        AdoptionRequestProcessor processor = new AdoptionRequestProcessor(serializerFactory, entityService, objectMapper);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("adopt1");
        request.setProcessorName("AdoptionRequestProcessor");
        
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(adoptionEntity));
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
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(petEntity.getId())), any(Pet.class));
    }
}
```
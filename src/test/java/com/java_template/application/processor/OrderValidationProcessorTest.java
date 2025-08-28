package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
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
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class OrderValidationProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real ObjectMapper configured to ignore unknown properties
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory (no Spring)
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a valid Pet payload with status AVAILABLE so order becomes CONFIRMED
        Pet pet = new Pet();
        pet.setPetId("pet-1");
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("AVAILABLE");
        pet.setAge(3);
        pet.setPhotoUrls(java.util.List.of("http://example.com/photo1.jpg"));
        pet.setTags(java.util.List.of("friendly"));

        JsonNode petJson = objectMapper.valueToTree(pet);
        DataPayload petDataPayload = new DataPayload();
        petDataPayload.setData(petJson);

        when(entityService.getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(java.util.List.of(petDataPayload)));

        // Instantiate processor with mocked EntityService
        OrderValidationProcessor processor = new OrderValidationProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Order that passes isValid() and references the pet above
        Order order = new Order();
        order.setOrderId("order-1");
        order.setPetId("pet-1");
        order.setBuyerName("Alice");
        order.setBuyerContact("alice@example.com");
        order.setType("adoption");
        order.setStatus("PLACED"); // initial status
        order.setPlacedAt("2025-01-01T00:00:00Z");

        JsonNode orderJson = objectMapper.valueToTree(order);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("OrderValidationProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(orderJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() {
                return null;
            }

            @Override
            public EntityProcessorCalculationRequest getEvent() {
                return request;
            }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned entity payload: status should be updated to CONFIRMED
        assertNotNull(response.getPayload());
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData);
        assertEquals("CONFIRMED", returnedData.get("status").asText());

        // Verify the processor queried EntityService for the referenced pet
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any(), eq(true));
    }
}
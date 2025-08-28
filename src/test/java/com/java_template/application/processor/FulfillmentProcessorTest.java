package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FulfillmentProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real Jackson serializers, no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a pet that is AVAILABLE and valid according to Pet.isValid()
        Pet pet = new Pet();
        pet.setPetId("pet-123");
        pet.setName("Fido");
        pet.setSpecies("Dog");
        pet.setStatus("AVAILABLE");
        pet.setAge(3);
        pet.setPhotoUrls(List.of()); // non-null empty list
        pet.setTags(List.of()); // non-null empty list

        JsonNode petJson = objectMapper.valueToTree(pet);
        DataPayload petPayload = new DataPayload();
        petPayload.setData(petJson);
        // meta must contain technical entityId used by FulfillmentProcessor
        String technicalId = UUID.randomUUID().toString();
        ObjectNode metaNode = objectMapper.createObjectNode();
        metaNode.put("entityId", technicalId);
        petPayload.setMeta(metaNode);

        // Stub EntityService.getItemsByCondition to return the pet payload
        when(entityService.getItemsByCondition(anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(List.of(petPayload)));

        // Stub updateItem to return the same UUID
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(technicalId)));

        // Create processor instance
        FulfillmentProcessor processor = new FulfillmentProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Order that should pass isValid() and reference the petId above
        Order order = new Order();
        order.setOrderId("order-1");
        order.setPetId("pet-123");
        order.setBuyerName("Alice");
        order.setBuyerContact("alice@example.com");
        order.setType("purchase"); // non-adoption path -> pet will be marked SOLD
        order.setStatus("PLACED");
        order.setPlacedAt("2025-01-01T00:00:00Z");
        order.setNotes(null);

        JsonNode orderJson = objectMapper.valueToTree(order);
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("FulfillmentProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(orderJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response
        assertNotNull(response, "Response must not be null");
        assertTrue(response.getSuccess(), "Response should be successful in sunny path");

        // Inspect returned payload for expected state changes (order status updated to COMPLETED)
        assertNotNull(response.getPayload(), "Response payload must not be null");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response payload data must not be null");

        Order returnedOrder = objectMapper.treeToValue(responseData, Order.class);
        assertNotNull(returnedOrder, "Returned order must be deserializable");
        assertEquals("COMPLETED", returnedOrder.getStatus(), "Order status should be COMPLETED after fulfillment");
        assertNotNull(returnedOrder.getNotes(), "Notes should be set after fulfillment");
        assertTrue(returnedOrder.getNotes().contains("Fulfillment completed"), "Notes should mention fulfillment completion");

        // Verify that EntityService.updateItem was invoked to persist pet status change
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(technicalId)), any());
    }
}
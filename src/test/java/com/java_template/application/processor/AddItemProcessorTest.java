package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AddItemProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Setup real Jackson serializers and factory (no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Create processor (no EntityService needed)
        AddItemProcessor processor = new AddItemProcessor(serializerFactory);

        // Build a minimal, valid Cart that will be transformed by processor logic
        Cart cart = new Cart();
        cart.setCartId(UUID.randomUUID().toString());
        cart.setCreatedAt("2020-01-01T00:00:00Z");
        cart.setStatus("NEW"); // should transition to ACTIVE when items present
        cart.setGrandTotal(0.0);
        cart.setTotalItems(0);

        Cart.Line line = new Cart.Line();
        line.setName("Example Item");
        line.setSku("SKU-123");
        line.setPrice(10.0);
        line.setQty(2);
        // Add the valid line
        cart.setLines(List.of(line));

        // Ensure entity passes validation before processing
        assertTrue(cart.isValid(), "Prepared cart must be valid for processing");

        // Build request with payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("AddItemProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(cart));
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
        assertNotNull(response, "response should not be null");
        assertTrue(response.getSuccess(), "response should indicate success");

        // Extract resulting entity from response payload and verify transformations
        assertNotNull(response.getPayload(), "response payload should be present");
        assertNotNull(response.getPayload().getData(), "response payload data should be present");

        Cart resultCart = objectMapper.convertValue(response.getPayload().getData(), Cart.class);
        assertNotNull(resultCart, "resulting cart should be deserializable");

        // After processing, totals should reflect line: qty=2, price=10.0 => totalItems=2, grandTotal=20.0
        assertEquals(2, resultCart.getTotalItems().intValue(), "totalItems should be recalculated to sum of line quantities");
        assertEquals(20.0, resultCart.getGrandTotal(), 0.0001, "grandTotal should be recalculated from line prices");

        // Status should transition from NEW to ACTIVE when items present
        assertEquals("ACTIVE", resultCart.getStatus(), "status should transition to ACTIVE when cart has items");

        // updatedAt should be set (non-null and non-empty)
        assertNotNull(resultCart.getUpdatedAt(), "updatedAt should be set");
        assertFalse(resultCart.getUpdatedAt().isBlank(), "updatedAt should not be blank");
    }
}
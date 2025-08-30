package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CreateOnFirstAddProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Setup real Jackson serializers and factory (no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Create processor with real serializer factory
        CreateOnFirstAddProcessor processor = new CreateOnFirstAddProcessor(serializerFactory);

        // Build a valid Cart entity that passes Cart.isValid()
        Cart cart = new Cart();
        cart.setCartId("cart-1");
        cart.setCreatedAt(OffsetDateTime.now().toString());
        cart.setStatus("NEW"); // expect processor to transition to ACTIVE when lines exist
        Cart.Line line = new Cart.Line();
        line.setName("Test Item");
        line.setSku("SKU-123");
        line.setPrice(10.0);
        line.setQty(2);
        cart.getLines().add(line);
        // totals must be present for isValid()
        cart.setTotalItems(2);
        cart.setGrandTotal(20.0);

        // Convert entity to JsonNode for payload
        JsonNode entityJson = objectMapper.valueToTree(cart);

        // Build request and payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("CreateOnFirstAddProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert core sunny-day behavior
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should be successful");

        // Inspect returned payload data for expected state change: status -> ACTIVE and totals computed
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData, "Returned data must be present");

        // Status should be ACTIVE after processing
        assertEquals("ACTIVE", returnedData.get("status").asText(), "Cart status should be transitioned to ACTIVE");

        // Total items and grand total should reflect line values (2 and 20.0)
        assertEquals(2, returnedData.get("totalItems").asInt(), "Total items should be 2");
        double grandTotal = returnedData.get("grandTotal").asDouble();
        assertEquals(20.0, grandTotal, 0.0001, "Grand total should be 20.0");
    }
}
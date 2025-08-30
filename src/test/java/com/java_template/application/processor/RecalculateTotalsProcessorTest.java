package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.Line;
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

import static org.junit.jupiter.api.Assertions.*;

public class RecalculateTotalsProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and factory (no Spring)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no Spring)
        RecalculateTotalsProcessor processor = new RecalculateTotalsProcessor(serializerFactory);

        // Build a valid Cart entity JSON that will pass Cart.isValid()
        Cart.Line line = new Line();
        line.setName("Test Item");
        line.setSku("sku-123");
        line.setPrice(10.0);
        line.setQty(2);

        Cart cart = new Cart();
        cart.setCartId("cart-1");
        cart.setCreatedAt("2020-01-01T00:00:00Z");
        // initial totals can be zero; processor will recalculate
        cart.setGrandTotal(0.0);
        cart.setStatus("OPEN");
        cart.setTotalItems(0);
        cart.setLines(java.util.List.of(line));

        JsonNode cartJson = objectMapper.valueToTree(cart);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("RecalculateTotalsProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(cartJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic success
        assertNotNull(response, "response should not be null");
        assertTrue(response.getSuccess(), "response should indicate success");
        assertNotNull(response.getPayload(), "response payload should not be null");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "response data should not be null");

        // Verify totals were recalculated: qty 2 * price 10.0 => grandTotal 20.0 and totalItems 2
        assertEquals(2, responseData.get("totalItems").asInt(), "totalItems should be recalculated to 2");
        assertEquals(20.0, responseData.get("grandTotal").asDouble(), 0.0001, "grandTotal should be recalculated to 20.0");
    }
}
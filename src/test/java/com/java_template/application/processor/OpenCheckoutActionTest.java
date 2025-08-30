package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.Line;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OpenCheckoutActionTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and SerializerFactory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        OpenCheckoutAction processor = new OpenCheckoutAction(serializerFactory);

        // Build a valid Cart entity that satisfies Cart.isValid() and is ACTIVE
        Cart cart = new Cart();
        cart.setCartId("cart-1");
        cart.setCreatedAt("2020-01-01T00:00:00Z");
        cart.setGrandTotal(100.0);
        cart.setStatus("ACTIVE");
        cart.setTotalItems(1);

        Line line = new Line();
        line.setName("Example Item");
        line.setSku("SKU-123");
        line.setPrice(100.0);
        line.setQty(1);
        List<Line> lines = new ArrayList<>();
        lines.add(line);
        cart.setLines(lines);

        // Convert entity to JsonNode payload
        JsonNode entityJson = objectMapper.valueToTree(cart);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("OpenCheckoutAction");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
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

        // Assert - sunny day expectations
        assertNotNull(response, "response should not be null");
        assertTrue(response.getSuccess(), "response should indicate success");

        assertNotNull(response.getPayload(), "response payload should be present");
        JsonNode respData = response.getPayload().getData();
        assertNotNull(respData, "response payload data should be present");

        // Processor should transition status ACTIVE -> CHECKING_OUT and set updatedAt
        assertEquals("CHECKING_OUT", respData.get("status").asText(), "status should be CHECKING_OUT");
        assertNotNull(respData.get("updatedAt"), "updatedAt should be set");
        assertFalse(respData.get("updatedAt").asText().isBlank(), "updatedAt should be a non-blank timestamp");
    }
}
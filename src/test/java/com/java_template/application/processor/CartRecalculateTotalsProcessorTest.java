```java
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
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

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CartRecalculateTotalsProcessorTest {

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
        when(entityService.getItemsByCondition(anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(new ArrayList<>()));

        CartRecalculateTotalsProcessor processor = new CartRecalculateTotalsProcessor(serializerFactory);

        Cart cart = new Cart();
        cart.setCartId("cart1");
        Cart.CartLine line1 = new Cart.CartLine();
        line1.setSku("sku1");
        line1.setName("Product 1");
        line1.setPrice(10.0);
        line1.setQty(2);
        
        Cart.CartLine line2 = new Cart.CartLine();
        line2.setSku("sku2");
        line2.setName("Product 2");
        line2.setPrice(20.0);
        line2.setQty(1);
        
        cart.setLines(new ArrayList<>(List.of(line1, line2)));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("request1");
        request.setRequestId("request1");
        request.setEntityId("entity1");
        request.setProcessorName("CartRecalculateTotalsProcessor");
        DataPayload payload = new DataPayload();
        JsonNode cartJson = objectMapper.valueToTree(cart);
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

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertNotNull(response.getPayload().getData());
        Cart processedCart = objectMapper.treeToValue(response.getPayload().getData(), Cart.class);
        assertEquals(2, processedCart.getTotalItems());
        assertEquals(40.0, processedCart.getGrandTotal());
    }
}
```
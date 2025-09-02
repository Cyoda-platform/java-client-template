```java
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.payment.version_1.Payment;
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

public class OrderCreateFromPaidProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        EntityService entityService = mock(EntityService.class);

        // Mocking Cart and Payment retrieval
        Cart cart = new Cart();
        cart.setCartId("extracted-cart-id");
        cart.setTotalItems(2);
        cart.setGrandTotal(100.0);
        cart.setLines(List.of(new Cart.CartLine("sku1", "Product 1", 50.0, 1),
                               new Cart.CartLine("sku2", "Product 2", 50.0, 1)));
        
        Payment payment = new Payment();
        payment.setPaymentId("extracted-payment-id");
        payment.setAmount(100.0);
        payment.setProvider("TestProvider");

        when(entityService.getFirstItemByCondition(eq(Cart.class), anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(cart)));
        when(entityService.getFirstItemByCondition(eq(Payment.class), anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(payment)));

        OrderCreateFromPaidProcessor processor = new OrderCreateFromPaidProcessor(serializerFactory);
        
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("OrderCreateFromPaidProcessor");
        
        // Creating a valid Order payload
        Order order = new Order();
        order.setOrderId("order1");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(order));
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
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());
        assertEquals("order1", response.getPayload().getData().get("orderId").asText());
        verify(entityService, atLeastOnce()).getFirstItemByCondition(eq(Cart.class), anyString(), anyInt(), any(), anyBoolean());
        verify(entityService, atLeastOnce()).getFirstItemByCondition(eq(Payment.class), anyString(), anyInt(), any(), anyBoolean());
    }
}
```
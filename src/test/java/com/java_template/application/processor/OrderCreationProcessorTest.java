package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.CartItem;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.reservation.version_1.Reservation;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class OrderCreationProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock EntityService only
        EntityService entityService = mock(EntityService.class);

        // Build domain objects for happy path
        String cartId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        String reservationId = UUID.randomUUID().toString();

        Cart cart = new Cart();
        cart.setId(cartId);
        cart.setUserId(userId);
        cart.setStatus("ACTIVE");
        cart.setLastUpdated(Instant.now().toString());
        CartItem cartItem = new CartItem();
        cartItem.setProductId(productId);
        cartItem.setQty(2);
        cartItem.setPriceSnapshot(10.0);
        cart.setItems(java.util.List.of(cartItem));

        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setName("Test User");

        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setCartId(cartId);
        reservation.setProductId(productId);
        reservation.setWarehouseId("warehouse-1");
        reservation.setQty(2);
        reservation.setStatus("ACTIVE");
        reservation.setCreatedAt(Instant.now().toString());
        reservation.setExpiresAt(Instant.now().plusSeconds(3600).toString());

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setAmount(20.0);
        payment.setApprovedAt(Instant.now().toString());
        payment.setCartId(cartId);
        payment.setCreatedAt(Instant.now().toString());
        payment.setStatus("APPROVED");
        payment.setUserId(userId);

        // Convert to DataPayloads (JsonNodes)
        DataPayload cartPayload = new DataPayload();
        cartPayload.setData(objectMapper.valueToTree(cart));
        DataPayload userPayload = new DataPayload();
        userPayload.setData(objectMapper.valueToTree(user));
        DataPayload reservationPayload = new DataPayload();
        reservationPayload.setData(objectMapper.valueToTree(reservation));

        // Stub EntityService interactions
        when(entityService.getItem(eq(UUID.fromString(cartId))))
                .thenReturn(CompletableFuture.completedFuture(cartPayload));
        when(entityService.getItem(eq(UUID.fromString(userId))))
                .thenReturn(CompletableFuture.completedFuture(userPayload));
        when(entityService.getItemsByCondition(eq(Reservation.ENTITY_NAME), eq(Reservation.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(reservationPayload)));
        // addItem for Order returns UUID
        when(entityService.addItem(eq(Order.ENTITY_NAME), eq(Order.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
        // addItem for Shipment returns UUID as well
        when(entityService.addItem(eq(com.java_template.application.entity.shipment.version_1.Shipment.ENTITY_NAME),
                eq(com.java_template.application.entity.shipment.version_1.Shipment.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor
        OrderCreationProcessor processor = new OrderCreationProcessor(serializerFactory, entityService, objectMapper);

        // Build request with Payment payload (use real DataPayload)
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(payment.getId());
        request.setProcessorName("OrderCreationProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(payment));
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
        assertNotNull(response, "response must not be null");
        assertTrue(response.getSuccess(), "response success must be true");

        // Inspect returned payload: should contain payment with orderId set
        assertNotNull(response.getPayload(), "payload must not be null");
        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "payload data must not be null");
        assertTrue(out.hasNonNull("orderId"), "orderId must be set on payment");
        assertFalse(out.get("orderId").asText().isBlank(), "orderId must not be blank");

        // Verify persistence interactions occurred
        verify(entityService, atLeastOnce()).addItem(eq(Order.ENTITY_NAME), eq(Order.ENTITY_VERSION), any());
        verify(entityService, atLeastOnce()).addItem(eq(com.java_template.application.entity.shipment.version_1.Shipment.ENTITY_NAME),
                eq(com.java_template.application.entity.shipment.version_1.Shipment.ENTITY_VERSION), any());
    }
}
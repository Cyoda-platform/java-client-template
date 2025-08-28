package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.CartItem;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.reservation.version_1.Reservation;
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
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CheckoutProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock EntityService (only allowed mock)
        EntityService entityService = mock(EntityService.class);

        // Build a valid cart entity (must satisfy Cart.isValid)
        Cart cart = new Cart();
        cart.setId("cart-1");
        cart.setUserId("user-1");
        cart.setStatus("OPEN");
        cart.setLastUpdated("2020-01-01T00:00:00Z");
        CartItem item = new CartItem();
        item.setProductId("prod-1");
        item.setQty(2);
        item.setPriceSnapshot(10.0);
        cart.setItems(java.util.List.of(item));

        // Build reservation that satisfies qty and is ACTIVE
        Reservation reservation = new Reservation();
        reservation.setId("res-1");
        reservation.setCartId(cart.getId());
        reservation.setProductId("prod-1");
        reservation.setWarehouseId("wh-1");
        reservation.setQty(2);
        reservation.setStatus("ACTIVE");
        reservation.setCreatedAt("2020-01-01T00:00:00Z");
        reservation.setExpiresAt("2099-01-01T00:00:00Z");

        DataPayload resPayload = new DataPayload();
        resPayload.setData(objectMapper.valueToTree(reservation));
        List<DataPayload> reservationPayloads = java.util.List.of(resPayload);

        // Stub getItemsByCondition to return the reservation list
        when(entityService.getItemsByCondition(
                eq(Reservation.ENTITY_NAME),
                eq(Reservation.ENTITY_VERSION),
                any(),
                anyBoolean()
        )).thenReturn(CompletableFuture.completedFuture(reservationPayloads));

        // Stub addItem to simulate creating a Payment
        when(entityService.addItem(
                eq(Payment.ENTITY_NAME),
                eq(Payment.ENTITY_VERSION),
                any()
        )).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor under test
        CheckoutProcessor processor = new CheckoutProcessor(serializerFactory, entityService, objectMapper);

        // Prepare request with payload (use real JSON via ObjectMapper)
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(cart.getId());
        request.setProcessorName("CheckoutProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(cart));
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(ctx);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Convert response payload back to Cart entity and assert checkout changes
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());
        Cart outCart = objectMapper.treeToValue(response.getPayload().getData(), Cart.class);
        assertNotNull(outCart);
        assertEquals("CHECKED_OUT", outCart.getStatus());
        assertNotNull(outCart.getLastUpdated());
        assertFalse(outCart.getLastUpdated().isBlank());

        // Verify entityService interactions occurred as expected
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Reservation.ENTITY_NAME), eq(Reservation.ENTITY_VERSION), any(), anyBoolean());
        verify(entityService, atLeastOnce()).addItem(eq(Payment.ENTITY_NAME), eq(Payment.ENTITY_VERSION), any());
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.CartItem;
import com.java_template.application.entity.product.version_1.Product;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CancelProcessorTest {

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

        // Mock EntityService only (per instructions)
        EntityService entityService = mock(EntityService.class);

        // Prepare product that reservation refers to
        Product product = new Product();
        product.setId(UUID.randomUUID().toString());
        product.setName("Test Product");
        product.setSku("TP-001");
        product.setWarehouseId(UUID.randomUUID().toString());
        product.setAvailableQuantity(5);
        product.setPrice(10.0);

        JsonNode productJson = objectMapper.valueToTree(product);
        DataPayload productPayload = new DataPayload();
        productPayload.setData(productJson);

        // Prepare reservation tied to the cart
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID().toString());
        // cartId will be set to cartId below
        reservation.setProductId(product.getId());
        reservation.setWarehouseId(product.getWarehouseId());
        reservation.setQty(2);
        reservation.setStatus("ACTIVE");
        reservation.setCreatedAt(Instant.now().toString());
        reservation.setExpiresAt(Instant.now().toString());

        JsonNode reservationJson = objectMapper.valueToTree(reservation);
        DataPayload reservationPayload = new DataPayload();
        reservationPayload.setData(reservationJson);

        // Stub entityService behavior:
        // - find reservations by condition -> return list containing our reservation payload
        when(entityService.getItemsByCondition(anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(List.of(reservationPayload)));

        // - getItem for product -> return product payload
        when(entityService.getItem(any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(productPayload));

        // - updateItem for reservation and product -> return completed future (payload not used)
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(new DataPayload()));

        // Build a valid Cart entity that will be processed
        Cart cart = new Cart();
        String cartId = UUID.randomUUID().toString();
        cart.setId(cartId);
        cart.setUserId(UUID.randomUUID().toString());
        cart.setStatus("ACTIVE");
        cart.setLastUpdated(Instant.now().toString());

        CartItem item = new CartItem();
        item.setProductId(product.getId());
        item.setQty(2);
        item.setPriceSnapshot(10.0);
        cart.setItems(List.of(item));

        // Ensure reservation.cartId matches the cart id used in the search condition
        reservation.setCartId(cartId);
        // update reservationPayload data to reflect cartId assignment
        reservationJson = objectMapper.valueToTree(reservation);
        reservationPayload.setData(reservationJson);

        // Create processor under test
        CancelProcessor processor = new CancelProcessor(serializerFactory, entityService, objectMapper);

        // Build request with payload (use real DataPayload)
        JsonNode cartJson = objectMapper.valueToTree(cart);
        DataPayload cartPayload = new DataPayload();
        cartPayload.setData(cartJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r-" + UUID.randomUUID());
        request.setRequestId("req-" + UUID.randomUUID());
        request.setEntityId(cartId);
        request.setProcessorName("CancelProcessor");
        request.setPayload(cartPayload);

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
        JsonNode outNode = response.getPayload().getData();
        assertNotNull(outNode);

        // The core sunny-day behavior: cart.status should be set to "CANCELLED"
        JsonNode statusNode = outNode.get("status");
        assertNotNull(statusNode);
        assertEquals("CANCELLED", statusNode.asText());

        // Verify entityService interactions for the sunny path occurred
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Reservation.ENTITY_NAME), eq(Reservation.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).updateItem(any(UUID.class), any());
        verify(entityService, atLeastOnce()).getItem(any(UUID.class));
    }
}
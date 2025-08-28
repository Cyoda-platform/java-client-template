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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CartProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService as required
        EntityService entityService = mock(EntityService.class);

        // Prepare product that will be returned by entityService.getItemsByCondition for Product
        Product product = new Product();
        String productId = UUID.randomUUID().toString();
        product.setId(productId);
        product.setName("Test Product");
        product.setSku("SKU-1");
        product.setWarehouseId(UUID.randomUUID().toString());
        product.setAvailableQuantity(5);
        product.setPrice(10.0);

        JsonNode productJson = objectMapper.valueToTree(product);
        DataPayload productPayload = new DataPayload();
        productPayload.setData(productJson);

        when(entityService.getItemsByCondition(eq(Product.ENTITY_NAME), eq(Product.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(productPayload)));

        // For reservations search, return empty list so processor will create a new reservation
        when(entityService.getItemsByCondition(eq(Reservation.ENTITY_NAME), eq(Reservation.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        // Stub addItem to succeed
        when(entityService.addItem(eq(Reservation.ENTITY_NAME), eq(Reservation.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor
        CartProcessor processor = new CartProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Cart entity (use the entity object directly)
        Cart cart = new Cart();
        String cartId = UUID.randomUUID().toString();
        cart.setId(cartId);
        cart.setUserId(UUID.randomUUID().toString());
        cart.setStatus("OPEN");
        cart.setLastUpdated("2020-01-01T00:00:00Z");

        CartItem item = new CartItem();
        item.setProductId(productId);
        item.setQty(2);
        item.setPriceSnapshot(10.0);
        cart.setItems(List.of(item));

        JsonNode cartJson = objectMapper.valueToTree(cart);
        DataPayload payload = new DataPayload();
        payload.setData(cartJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(cartId);
        request.setProcessorName("CartProcessor");
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

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertTrue(out.hasNonNull("lastUpdated"));
        String newLastUpdated = out.get("lastUpdated").asText();
        assertFalse(newLastUpdated.isBlank());
        // lastUpdated should have been refreshed (different from original)
        assertNotEquals("2020-01-01T00:00:00Z", newLastUpdated);

        // Verify a reservation was created
        verify(entityService, atLeastOnce()).addItem(eq(Reservation.ENTITY_NAME), eq(Reservation.ENTITY_VERSION), any());
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
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

public class ReleaseProcessorTest {

    @Test
    void sunnyDay_release_reservations_process_test() throws Exception {
        // Arrange - serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a reservation that will be found for the cart and needs releasing
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID().toString());
        reservation.setCartId(UUID.randomUUID().toString()); // will replace with cart id below
        reservation.setProductId(UUID.randomUUID().toString());
        reservation.setWarehouseId(UUID.randomUUID().toString());
        reservation.setQty(1);
        reservation.setStatus("HELD"); // not RELEASED so processor should process it
        reservation.setCreatedAt(Instant.now().toString());
        reservation.setExpiresAt(Instant.now().plusSeconds(3600).toString());

        // Prepare a valid cart that triggers release (status RELEASED)
        Cart cart = new Cart();
        cart.setId(UUID.randomUUID().toString());
        cart.setUserId(UUID.randomUUID().toString());
        cart.setStatus("RELEASED");
        cart.setLastUpdated(Instant.now().toString());
        Cart.CartItem item = new Cart.CartItem();
        item.setProductId(UUID.randomUUID().toString());
        item.setQty(1);
        item.setPriceSnapshot(10.0);
        cart.setItems(java.util.List.of(item));

        // Ensure reservation references this cart
        reservation.setCartId(cart.getId());

        // Prepare DataPayload list returned from EntityService.getItemsByCondition
        DataPayload reservationPayload = new DataPayload();
        reservationPayload.setData(objectMapper.valueToTree(reservation));
        List<DataPayload> found = List.of(reservationPayload);

        // Stub getItemsByCondition to return our reservation
        when(entityService.getItemsByCondition(eq(Reservation.ENTITY_NAME), eq(Reservation.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(found));

        // Stub updateItem to succeed
        when(entityService.updateItem(any(UUID.class), any())).thenReturn(CompletableFuture.completedFuture(null));

        // Instantiate processor
        ReleaseProcessor processor = new ReleaseProcessor(serializerFactory, entityService, objectMapper);

        // Build request with cart payload (use real serializer behavior)
        JsonNode cartJson = objectMapper.valueToTree(cart);
        DataPayload requestPayload = new DataPayload();
        requestPayload.setData(cartJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("req-1");
        request.setRequestId("req-1");
        request.setEntityId(cart.getId());
        request.setProcessorName("ReleaseProcessor");
        request.setPayload(requestPayload);

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

        // Response payload should contain the cart with same id and status RELEASED
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertEquals(cart.getId(), out.get("id").asText());
        assertEquals("RELEASED", out.get("status").asText());

        // Verify that updateItem was invoked for the reservation (at least once)
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(reservation.getId())), any());
    }
}
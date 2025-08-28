package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class ProductDeactivateProcessorTest {

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

        // Mock only EntityService as required by processor constructor
        EntityService entityService = mock(EntityService.class);

        // Prepare a reservation that is ACTIVE and valid so processor will attempt to update it
        Reservation reservation = new Reservation();
        String reservationId = UUID.randomUUID().toString();
        reservation.setId(reservationId);
        reservation.setCartId(UUID.randomUUID().toString());
        reservation.setProductId(UUID.randomUUID().toString());
        reservation.setWarehouseId(UUID.randomUUID().toString());
        reservation.setQty(1);
        reservation.setStatus("ACTIVE");
        reservation.setCreatedAt("2025-01-01T00:00:00Z");
        reservation.setExpiresAt("2025-12-31T23:59:59Z");

        DataPayload reservationPayload = new DataPayload();
        reservationPayload.setData(objectMapper.valueToTree(reservation));

        // Stub EntityService to return the ACTIVE reservation and to succeed on updateItem
        when(entityService.getItemsByCondition(anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(List.of(reservationPayload)));
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(reservationId)));

        // Construct the processor under test
        ProductDeactivateProcessor processor = new ProductDeactivateProcessor(serializerFactory, entityService, objectMapper);

        // Create a valid Product entity for the sunny path
        Product product = new Product();
        String productId = UUID.randomUUID().toString();
        product.setId(productId);
        product.setName("Test Product");
        product.setSku("TP-001");
        product.setWarehouseId(UUID.randomUUID().toString());
        product.setAvailableQuantity(5);
        product.setPrice(19.99);

        // Build request payload using real DataPayload and ObjectMapper
        DataPayload productPayload = new DataPayload();
        productPayload.setData(objectMapper.valueToTree(product));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(productId);
        request.setProcessorName("ProductDeactivateProcessor");
        request.setPayload(productPayload);

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

        // Inspect returned payload - availableQuantity should be set to 0 by processor
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertTrue(out.has("availableQuantity"));
        assertEquals(0, out.get("availableQuantity").asInt());

        // Verify EntityService update was invoked for the reservation release
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(reservationId)), any());
    }
}
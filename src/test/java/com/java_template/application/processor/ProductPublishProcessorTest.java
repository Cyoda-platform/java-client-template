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

public class ProductPublishProcessorTest {

    @Test
    void sunnyDay_product_publish_allocates_and_expires_reservation() throws Exception {
        // Arrange - serializer setup (real Jackson serializers)
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Create product with availableQuantity = 5
        Product product = new Product();
        product.setId(UUID.randomUUID().toString());
        product.setName("Test Product");
        product.setSku("TP-001");
        product.setWarehouseId(UUID.randomUUID().toString());
        product.setAvailableQuantity(5);
        product.setPrice(10.0);

        // Create two reservations (both ACTIVE): first qty=3 (will be satisfied), second qty=4 (will be expired)
        Reservation r1 = new Reservation();
        r1.setId(UUID.randomUUID().toString());
        r1.setCartId(UUID.randomUUID().toString());
        r1.setProductId(product.getId());
        r1.setWarehouseId(product.getWarehouseId());
        r1.setQty(3);
        r1.setStatus("ACTIVE");
        r1.setCreatedAt("2025-01-01T00:00:00Z");
        r1.setExpiresAt("2025-12-31T00:00:00Z");

        Reservation r2 = new Reservation();
        String r2Id = UUID.randomUUID().toString();
        r2.setId(r2Id);
        r2.setCartId(UUID.randomUUID().toString());
        r2.setProductId(product.getId());
        r2.setWarehouseId(product.getWarehouseId());
        r2.setQty(4);
        r2.setStatus("ACTIVE");
        r2.setCreatedAt("2025-01-02T00:00:00Z");
        r2.setExpiresAt("2025-12-31T00:00:00Z");

        // Prepare DataPayloads as returned by EntityService.getItemsByCondition
        DataPayload p1 = new DataPayload();
        p1.setData(objectMapper.valueToTree(r1));
        DataPayload p2 = new DataPayload();
        p2.setData(objectMapper.valueToTree(r2));

        when(entityService.getItemsByCondition(eq(Reservation.ENTITY_NAME), eq(Reservation.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(p1, p2)));

        // When updating reservation (to EXPIRED), return a completed UUID future
        when(entityService.updateItem(eq(UUID.fromString(r2Id)), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(r2Id)));

        // Instantiate processor (no Spring)
        ProductPublishProcessor processor = new ProductPublishProcessor(serializerFactory, entityService, objectMapper);

        // Build request with payload being the product (use real DataPayload)
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("req1");
        request.setRequestId("req1");
        request.setEntityId(product.getId());
        request.setProcessorName("ProductPublishProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(product));
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

        // Assert basic response
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned product payload
        assertNotNull(response.getPayload());
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // After satisfying r1 (qty 3) from available 5, remaining should be 2
        assertEquals(2, out.get("availableQuantity").asInt());

        // Verify updateItem was called to expire the second reservation
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(r2Id)), argThat(arg ->
                arg != null && arg instanceof Reservation && "EXPIRED".equals(((Reservation) arg).getStatus())
        ));
    }
}
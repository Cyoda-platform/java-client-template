package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
import com.java_template.application.entity.product.version_1.Product;
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

public class ReconciliationProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange: configure ObjectMapper and real serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a Product that will be returned by getItemsByCondition
        Product product = new Product();
        product.setProductId("prod-1");
        product.setName("Test Product");
        product.setPrice(9.99);
        product.setMetadata(null);

        JsonNode productJson = objectMapper.valueToTree(product);
        DataPayload productPayload = new DataPayload();
        productPayload.setData(productJson);
        ObjectNode meta = objectMapper.createObjectNode();
        String technicalId = UUID.randomUUID().toString();
        meta.put("entityId", technicalId);
        productPayload.setMeta(meta);

        when(entityService.getItemsByCondition(eq(Product.ENTITY_NAME), eq(Product.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(productPayload)));

        when(entityService.updateItem(eq(UUID.fromString(technicalId)), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(technicalId)));

        // Create processor instance (no Spring)
        ReconciliationProcessor processor = new ReconciliationProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid InventorySnapshot that triggers the "needs restock" branch (stockLevel < restockThreshold)
        InventorySnapshot snapshot = new InventorySnapshot();
        snapshot.setSnapshotId("snap-1");
        snapshot.setProductId(product.getProductId());
        snapshot.setSnapshotAt("2025-08-25T00:00:00Z");
        snapshot.setStockLevel(5);
        snapshot.setRestockThreshold(10);

        JsonNode snapshotJson = objectMapper.valueToTree(snapshot);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ReconciliationProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(snapshotJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert minimal sunny-day expectations
        assertNotNull(response);
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        JsonNode respData = response.getPayload().getData();
        assertNotNull(respData);
        assertEquals("snap-1", respData.get("snapshotId").asText());

        // Verify EntityService was queried and update called to mark NEEDS_RESTOCK
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Product.ENTITY_NAME), eq(Product.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(technicalId)), any());
    }
}
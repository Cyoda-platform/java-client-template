package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class StockEvaluatorProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare InventorySnapshot that passes isValid()
        InventorySnapshot snapshot = new InventorySnapshot();
        snapshot.setSnapshotId("snap-1");
        snapshot.setProductId("prod-1");
        snapshot.setSnapshotAt("2025-08-25T09:15:00Z");
        snapshot.setStockLevel(5); // low stock
        snapshot.setRestockThreshold(10); // threshold higher than stock -> needsRestock = true

        // Prepare Product returned by EntityService.getItemsByCondition and that passes Product.isValid()
        Product product = new Product();
        product.setProductId("prod-1");
        product.setName("Test Product");
        product.setPrice(1.0);
        product.setMetadata(null);

        // Build DataPayload with product data and meta containing technical entityId (UUID)
        DataPayload productPayload = new DataPayload();
        // DataPayload has setters in runtime; set data as JsonNode converted from product
        JsonNode productDataNode = objectMapper.valueToTree(product);
        productPayload.setData(productDataNode);
        ObjectNode metaNode = objectMapper.createObjectNode();
        String technicalId = UUID.randomUUID().toString();
        metaNode.put("entityId", technicalId);
        productPayload.setMeta(metaNode);

        // Stub EntityService.getItemsByCondition to return the product payload
        when(entityService.getItemsByCondition(
                eq(Product.ENTITY_NAME),
                eq(Product.ENTITY_VERSION),
                any(),
                eq(true)
        )).thenReturn(CompletableFuture.completedFuture(List.of(productPayload)));

        // Stub updateItem to return the UUID of updated item
        when(entityService.updateItem(any(UUID.class), any()))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    return CompletableFuture.completedFuture(id);
                });

        // Create processor with real serializerFactory and mocked service
        StockEvaluatorProcessor processor = new StockEvaluatorProcessor(serializerFactory, entityService, objectMapper);

        // Build request with payload containing the InventorySnapshot JSON
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("StockEvaluatorProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(snapshot));
        request.setPayload(payload);

        // Minimal CyodaEventContext
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload data - should correspond to the InventorySnapshot we provided
        DataPayload responsePayload = response.getPayload();
        assertNotNull(responsePayload);
        JsonNode responseData = responsePayload.getData();
        assertNotNull(responseData);
        assertEquals("snap-1", responseData.get("snapshotId").asText());
        assertEquals("prod-1", responseData.get("productId").asText());

        // Verify that EntityService.updateItem was called to update the Product with needsRestock flag
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(entityService, atLeastOnce()).updateItem(uuidCaptor.capture(), productCaptor.capture());

        // Assert the technical id passed to updateItem matches the meta entityId
        assertEquals(UUID.fromString(technicalId), uuidCaptor.getValue());

        // The processor should have set metadata containing needsRestock = true
        Product updatedProduct = productCaptor.getValue();
        assertNotNull(updatedProduct.getMetadata());
        // Parse metadata to assert needsRestock flag present and true
        JsonNode metadataNode = objectMapper.readTree(updatedProduct.getMetadata());
        assertTrue(metadataNode.has("needsRestock"));
        assertTrue(metadataNode.get("needsRestock").asBoolean());
    }
}
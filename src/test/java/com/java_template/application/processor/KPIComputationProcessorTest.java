package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
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
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class KPIComputationProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure to ignore unknown properties during deserialization as required
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a valid SalesRecord that will pass isValid()
        SalesRecord salesRecord = new SalesRecord();
        salesRecord.setRecordId("rec-1");
        salesRecord.setDateSold("2025-08-25T10:00:00Z");
        salesRecord.setProductId("prod-uuid-1");
        salesRecord.setQuantity(10); // >= MIN_SALES_QTY to avoid UNDERPERFORMING by qty
        salesRecord.setRevenue(200.0); // unitPrice = 20.0
        salesRecord.setRawSource("{\"source\":\"pos\"}");

        JsonNode salesJson = objectMapper.valueToTree(salesRecord);

        // Prepare InventorySnapshot payload such that turnover = qty / stockLevel = 10 / 50 = 0.2 (> 0.1)
        InventorySnapshot snapshot = new InventorySnapshot();
        snapshot.setSnapshotId("snap-1");
        snapshot.setProductId(salesRecord.getProductId());
        snapshot.setSnapshotAt("2025-08-25T09:00:00Z");
        snapshot.setStockLevel(50);
        snapshot.setRestockThreshold(5);

        DataPayload invPayload = new DataPayload();
        invPayload.setData(objectMapper.valueToTree(snapshot));
        invPayload.setMeta(objectMapper.createObjectNode()); // meta not used for inventory

        // Prepare Product payload to be updated by processor
        Product product = new Product();
        product.setProductId(salesRecord.getProductId());
        product.setName("Test Product");
        product.setPrice(9.99);
        product.setMetadata(null); // processor will create metadata node

        DataPayload prodPayload = new DataPayload();
        prodPayload.setData(objectMapper.valueToTree(product));
        ObjectNode prodMeta = objectMapper.createObjectNode();
        UUID technicalId = UUID.randomUUID();
        prodMeta.put("entityId", technicalId.toString());
        prodPayload.setMeta(prodMeta);

        // Stub getItemsByCondition to return inventory snapshot for InventorySnapshot and product for Product
        when(entityService.getItemsByCondition(anyString(), any(), any(), anyBoolean()))
                .thenAnswer(invocation -> {
                    String modelName = invocation.getArgument(0);
                    if (InventorySnapshot.ENTITY_NAME.equals(modelName)) {
                        return CompletableFuture.completedFuture(List.of(invPayload));
                    } else if (Product.ENTITY_NAME.equals(modelName)) {
                        return CompletableFuture.completedFuture(List.of(prodPayload));
                    } else {
                        return CompletableFuture.completedFuture(List.of());
                    }
                });

        // Stub updateItem to return completed future
        when(entityService.updateItem(any(UUID.class), any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return CompletableFuture.completedFuture(id);
        });

        // Instantiate processor (pass mocked entityService)
        KPIComputationProcessor processor = new KPIComputationProcessor(serializerFactory, entityService, objectMapper);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("KPIComputationProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(salesJson);
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

        // Assert the returned payload contains the SalesRecord with expected recordId
        assertNotNull(response.getPayload());
        JsonNode respData = response.getPayload().getData();
        assertNotNull(respData);
        assertEquals("rec-1", respData.get("recordId").asText());

        // Verify that updateItem was called to persist product metadata update
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(entityService, atLeastOnce()).updateItem(eq(technicalId), productCaptor.capture());

        Product updatedProduct = productCaptor.getValue();
        assertNotNull(updatedProduct.getMetadata());

        // Parse metadata and assert performanceTag is set to NORMAL (sunny-day)
        JsonNode metadataNode = objectMapper.readTree(updatedProduct.getMetadata());
        assertEquals("NORMAL", metadataNode.get("performanceTag").asText());
        assertEquals(10, metadataNode.get("lastSaleQuantity").asInt());
    }
}
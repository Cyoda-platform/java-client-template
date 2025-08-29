package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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

public class RestockAlertProcessorTest {

    @Test
    void sunnyDay_restocks_create_weekly_report_and_return_entity() throws Exception {
        // Setup real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(eq(WeeklyReport.ENTITY_NAME), eq(WeeklyReport.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Construct processor with real serializers and mocked service
        RestockAlertProcessor processor = new RestockAlertProcessor(serializerFactory, entityService, objectMapper);

        // Create a valid InventorySnapshot that will trigger restock (stockLevel < restockThreshold)
        InventorySnapshot snapshot = new InventorySnapshot();
        snapshot.setSnapshotId("snap-1");
        snapshot.setProductId("product-123");
        snapshot.setSnapshotAt("2025-08-25T10:00:00Z");
        snapshot.setStockLevel(5);
        snapshot.setRestockThreshold(10);

        // Convert entity to JsonNode for payload
        JsonNode entityJson = objectMapper.valueToTree(snapshot);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("RestockAlertProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        // Assert payload contains the entity and fields preserved
        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData, "Returned payload data should not be null");

        InventorySnapshot returnedEntity = objectMapper.treeToValue(returnedData, InventorySnapshot.class);
        assertEquals(snapshot.getSnapshotId(), returnedEntity.getSnapshotId());
        assertEquals(snapshot.getProductId(), returnedEntity.getProductId());
        assertEquals(snapshot.getStockLevel(), returnedEntity.getStockLevel());
        assertEquals(snapshot.getRestockThreshold(), returnedEntity.getRestockThreshold());

        // Verify that a WeeklyReport was attempted to be created via EntityService
        verify(entityService, atLeastOnce()).addItem(eq(WeeklyReport.ENTITY_NAME), eq(WeeklyReport.ENTITY_VERSION), any());
    }
}
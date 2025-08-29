package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
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

public class TemplateApplierProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare SalesRecord payloads
        SalesRecord sr1 = new SalesRecord();
        sr1.setRecordId("sr-1");
        sr1.setDateSold("2025-08-25T10:00:00Z");
        sr1.setProductId("prod-1");
        sr1.setQuantity(2);
        sr1.setRevenue(100.0);
        sr1.setRawSource("{\"source\":\"ingest\"}");

        SalesRecord sr2 = new SalesRecord();
        sr2.setRecordId("sr-2");
        sr2.setDateSold("2025-08-25T11:00:00Z");
        sr2.setProductId("prod-1");
        sr2.setQuantity(1);
        sr2.setRevenue(50.0);
        sr2.setRawSource("{\"source\":\"ingest\"}");

        DataPayload salesPayload1 = new DataPayload();
        salesPayload1.setData(objectMapper.valueToTree(sr1));
        DataPayload salesPayload2 = new DataPayload();
        salesPayload2.setData(objectMapper.valueToTree(sr2));
        List<DataPayload> salesList = List.of(salesPayload1, salesPayload2);

        // Prepare Product payloads
        Product p1 = new Product();
        p1.setProductId("prod-1");
        p1.setName("Widget");
        p1.setCategory("Gadgets");
        p1.setPrice(9.99);
        DataPayload prodPayload = new DataPayload();
        prodPayload.setData(objectMapper.valueToTree(p1));
        List<DataPayload> productList = List.of(prodPayload);

        // Prepare InventorySnapshot payloads (low stock to trigger restock)
        InventorySnapshot snap1 = new InventorySnapshot();
        snap1.setSnapshotId("snap-1");
        snap1.setProductId("prod-1");
        snap1.setSnapshotAt("2025-08-25T00:00:00Z");
        snap1.setStockLevel(1);
        snap1.setRestockThreshold(5);
        DataPayload invPayload = new DataPayload();
        invPayload.setData(objectMapper.valueToTree(snap1));
        List<DataPayload> inventoryList = List.of(invPayload);

        // Stub EntityService.getItems for the three entity types
        when(entityService.getItems(eq(SalesRecord.ENTITY_NAME), eq(SalesRecord.ENTITY_VERSION), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(salesList));
        when(entityService.getItems(eq(Product.ENTITY_NAME), eq(Product.ENTITY_VERSION), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(productList));
        when(entityService.getItems(eq(InventorySnapshot.ENTITY_NAME), eq(InventorySnapshot.ENTITY_VERSION), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(inventoryList));

        // Instantiate processor with real serializerFactory, mocked entityService and real objectMapper
        TemplateApplierProcessor processor = new TemplateApplierProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid WeeklyReport that will pass isValid() and trigger template application
        WeeklyReport report = new WeeklyReport();
        report.setReportId("weekly-2025-w34");
        report.setGeneratedAt("2025-08-25T09:15:00Z"); // required by isValid()
        report.setWeekStart("2025-08-18");
        report.setStatus("GENERATING"); // will trigger template application

        JsonNode reportJson = objectMapper.valueToTree(report);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("TemplateApplierProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(reportJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData, "Result data should be present");

        // Core sunny-path expectations: status moved to TEMPLATE_APPLIED, summary set, attachmentUrl set
        assertEquals("TEMPLATE_APPLIED", resultData.get("status").asText());
        assertNotNull(resultData.get("summary"));
        String summary = resultData.get("summary").asText();
        assertTrue(summary.contains("Top seller"), "Summary should contain top seller info");
        assertTrue(resultData.hasNonNull("attachmentUrl"), "Attachment URL should be set on the report");
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ScheduleReportProcessorTest {

    @Test
    void sunnyDay_scheduleReportProcessor_process_setsDispatchedAndAttachment() throws Exception {
        // Setup real ObjectMapper as required
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare sample sales records
        SalesRecord sale1 = new SalesRecord();
        sale1.setRecordId("s1");
        sale1.setDateSold("2025-08-25T10:00:00Z");
        sale1.setProductId("prod-1");
        sale1.setQuantity(3);
        sale1.setRevenue(30.0);
        sale1.setRawSource("{\"sample\":true}");

        SalesRecord sale2 = new SalesRecord();
        sale2.setRecordId("s2");
        sale2.setDateSold("2025-08-25T11:00:00Z");
        sale2.setProductId("prod-2");
        sale2.setQuantity(2);
        sale2.setRevenue(20.0);
        sale2.setRawSource("{\"sample\":true}");

        DataPayload salesPayload1 = new DataPayload();
        salesPayload1.setData(objectMapper.valueToTree(sale1));
        DataPayload salesPayload2 = new DataPayload();
        salesPayload2.setData(objectMapper.valueToTree(sale2));

        List<DataPayload> salesList = List.of(salesPayload1, salesPayload2);

        // Prepare sample inventory snapshot (low stock)
        InventorySnapshot snap = new InventorySnapshot();
        snap.setSnapshotId("snap-1");
        snap.setProductId("prod-1");
        snap.setSnapshotAt("2025-08-25T09:00:00Z");
        snap.setStockLevel(2);
        snap.setRestockThreshold(5);

        DataPayload invPayload = new DataPayload();
        invPayload.setData(objectMapper.valueToTree(snap));

        List<DataPayload> invList = List.of(invPayload);

        // Stub entityService.getItems for SalesRecord and InventorySnapshot
        when(entityService.getItems(eq(SalesRecord.ENTITY_NAME), eq(SalesRecord.ENTITY_VERSION), eq(null), eq(null), eq(null)))
                .thenReturn(CompletableFuture.completedFuture(salesList));

        when(entityService.getItems(eq(InventorySnapshot.ENTITY_NAME), eq(InventorySnapshot.ENTITY_VERSION), eq(null), eq(null), eq(null)))
                .thenReturn(CompletableFuture.completedFuture(invList));

        // Instantiate processor with mocked EntityService
        ScheduleReportProcessor processor = new ScheduleReportProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid WeeklyReport payload (must satisfy isValid() before processing)
        WeeklyReport report = new WeeklyReport();
        report.setReportId("weekly-2025-W34");
        report.setGeneratedAt("2025-08-25T00:00:00Z"); // required by isValid()
        report.setWeekStart("2025-08-18");
        report.setStatus("DRAFT"); // initial status must be non-blank

        JsonNode reportJson = objectMapper.valueToTree(report);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ScheduleReportProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(reportJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext implementation
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload and verify processor updated the entity
        assertNotNull(response.getPayload());
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData);

        WeeklyReport resultReport = objectMapper.treeToValue(returnedData, WeeklyReport.class);
        assertNotNull(resultReport);

        // Core sunny-day assertions: status updated to DISPATCHED, attachmentUrl set, summary contains expected phrase
        assertEquals("DISPATCHED", resultReport.getStatus());
        assertNotNull(resultReport.getAttachmentUrl());
        assertFalse(resultReport.getAttachmentUrl().isBlank());
        assertNotNull(resultReport.getSummary());
        assertTrue(resultReport.getSummary().contains("Sales records processed"));
    }
}
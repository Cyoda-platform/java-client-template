package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ReportGeneratorProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - ObjectMapper & serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked
        EntityService entityService = mock(EntityService.class);
        // Return empty lists for any getItems calls (sales, products, inventory) to exercise the no-sales/no-restock path
        when(entityService.getItems(anyString(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

        // Create processor with real serializers and mocked EntityService
        ReportGeneratorProcessor processor = new ReportGeneratorProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid WeeklyReport payload (must be valid before processing)
        WeeklyReport report = new WeeklyReport();
        report.setReportId("weekly-2025-01");
        report.setGeneratedAt("2025-01-01T00:00:00Z"); // initial value; processor will overwrite
        report.setWeekStart("2025-01-01");
        report.setStatus("PENDING");

        JsonNode entityJson = objectMapper.valueToTree(report);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ReportGeneratorProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
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

        // Assert
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should succeed on sunny path");
        assertNotNull(response.getPayload(), "Response payload should be present");
        assertNotNull(response.getPayload().getData(), "Response payload data should be present");

        JsonNode outData = response.getPayload().getData();
        // Processor sets status to GENERATING on successful generation path
        assertTrue(outData.has("status"), "Output entity should contain status");
        assertEquals("GENERATING", outData.get("status").asText(), "Status should be GENERATING after processing");

        // Summary should indicate no sales data for the provided week (sunny path with empty data lists)
        assertTrue(outData.has("summary"), "Output entity should contain summary");
        String summary = outData.get("summary").asText();
        assertTrue(summary.contains("No sales data found for week starting") || summary.contains("No sales data found"),
                "Summary should indicate no sales data when none available");

        // Verify that entityService.getItems was used (at least once) to attempt to load related entities
        verify(entityService, atLeastOnce()).getItems(anyString(), any(), any(), any(), any());
    }
}
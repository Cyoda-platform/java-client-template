package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class ArchiveReportProcessorTest {

    @Test
    void sunnyDay_archive_old_dispatched_report() {
        // Setup real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked because constructor requires it
        EntityService entityService = mock(EntityService.class);

        // Instantiate processor with real serializerFactory and objectMapper
        ArchiveReportProcessor processor = new ArchiveReportProcessor(serializerFactory, entityService, objectMapper);

        // Build a WeeklyReport that is dispatched and older than 30 days to trigger archive
        WeeklyReport weeklyReport = new WeeklyReport();
        weeklyReport.setReportId("weekly-summary-2025-W01");
        OffsetDateTime oldDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(31);
        weeklyReport.setGeneratedAt(oldDate.toString());
        weeklyReport.setWeekStart(oldDate.toLocalDate().toString());
        weeklyReport.setStatus("DISPATCHED");
        weeklyReport.setSummary("Summary text");

        // Convert entity to JsonNode payload
        JsonNode entityJson = objectMapper.valueToTree(weeklyReport);

        // Build request and payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(UUID.randomUUID().toString());
        request.setProcessorName("ArchiveReportProcessor");
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

        // Assert core sunny-path expectations
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData, "Result data should be present");
        assertEquals("ARCHIVED", resultData.get("status").asText(), "WeeklyReport status should be updated to ARCHIVED");
    }
}
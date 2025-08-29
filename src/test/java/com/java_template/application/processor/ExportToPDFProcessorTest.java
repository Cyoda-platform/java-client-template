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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class ExportToPDFProcessorTest {

    @Test
    void sunnyDay_export_to_pdf_processor_test() {
        // Arrange: configure real ObjectMapper and serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked as required by constructor
        EntityService entityService = mock(EntityService.class);

        ExportToPDFProcessor processor = new ExportToPDFProcessor(serializerFactory, entityService, objectMapper);

        // Create a valid WeeklyReport that satisfies isValid()
        WeeklyReport weeklyReport = new WeeklyReport();
        weeklyReport.setReportId("weekly-summary-2025-W34");
        weeklyReport.setGeneratedAt("2025-08-25T09:15:00Z"); // required non-blank
        weeklyReport.setWeekStart("2025-08-18"); // required non-blank
        weeklyReport.setStatus("DISPATCHED"); // required non-blank
        weeklyReport.setSummary("Test summary for weekly report");

        JsonNode entityJson = objectMapper.valueToTree(weeklyReport);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ExportToPDFProcessor");

        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert: basic success and that processor set attachmentUrl and updated status to READY
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processor should report success");

        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData, "Returned data must not be null");

        // Status should be updated to READY by the processor
        assertTrue(returnedData.has("status"), "Returned entity should contain status");
        assertEquals("READY", returnedData.get("status").asText(), "Status should be READY after export");

        // attachmentUrl should be set and contain expected prefix
        assertTrue(returnedData.has("attachmentUrl"), "Returned entity should contain attachmentUrl");
        String attachmentUrl = returnedData.get("attachmentUrl").asText();
        assertNotNull(attachmentUrl);
        assertFalse(attachmentUrl.isBlank());
        assertTrue(attachmentUrl.startsWith("https://filestore/reports/"), "attachmentUrl should point to filestore reports");
    }
}
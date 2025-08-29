package com.java_template.application.processor;

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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class EmailDispatchProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and serializer factory
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked per instructions
        EntityService entityService = mock(EntityService.class);

        // Create processor with real serializers and mocked EntityService
        EmailDispatchProcessor processor = new EmailDispatchProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid WeeklyReport entity but with no attachmentUrl so processor will mark FAILED
        WeeklyReport report = new WeeklyReport();
        report.setReportId("weekly-summary-2025-W34");
        report.setGeneratedAt("2025-08-25T09:15:00Z");
        report.setWeekStart("2025-08-18");
        report.setStatus("PENDING");
        report.setSummary("Initial summary");
        // attachmentUrl intentionally left null to exercise branch that avoids external HTTP

        JsonNode entityJson = objectMapper.valueToTree(report);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("EmailDispatchProcessor");
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

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect the returned payload to ensure processor applied the expected sunny-path changes
        assertNotNull(response.getPayload());
        JsonNode outData = response.getPayload().getData();
        assertNotNull(outData);

        // Because attachmentUrl was missing, processor should set status to "FAILED"
        assertEquals("FAILED", outData.get("status").asText());

        // Summary should have appended message about missing attachmentUrl
        String summaryOut = outData.get("summary").asText();
        assertTrue(summaryOut.contains("missing attachmentUrl"));
    }
}
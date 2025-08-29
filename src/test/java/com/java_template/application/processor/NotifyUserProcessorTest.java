package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.application.entity.report.version_1.Report;
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
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NotifyUserProcessorTest {

    @Test
    void sunnyDay_notify_user_marks_completed_when_report_found() throws Exception {
        // Arrange - real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked
        EntityService entityService = mock(EntityService.class);

        // Prepare a Report that will be found by the processor (must be valid)
        String technicalId = "tech-123";
        Report foundReport = new Report();
        foundReport.setName("Monthly Report");
        foundReport.setReportId("report-1");
        foundReport.setJobTechnicalId(technicalId);
        foundReport.setStatus("GENERATED");
        foundReport.setGeneratedAt("2025-01-01T00:00:00Z");
        foundReport.setCreatedBy("report-generator");

        DataPayload reportPayload = new DataPayload();
        reportPayload.setData(objectMapper.valueToTree(foundReport));

        when(entityService.getItemsByCondition(anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(List.of(reportPayload)));

        // Instantiate processor (no Spring)
        NotifyUserProcessor processor = new NotifyUserProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid ReportJob entity JSON for the request payload
        ReportJob job = new ReportJob();
        job.setName("Generate monthly");
        job.setRequestedAt("2025-01-01T00:00:00Z");
        job.setRequestedBy("user@example.com");
        job.setStatus("PENDING");
        // leave filters null to avoid additional validation requirements

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(technicalId); // this will be used to search reports
        request.setProcessorName("NotifyUserProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(jobJson);
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
        assertTrue(response.getSuccess(), "Processor should report success on sunny path");

        // Extract resulting entity from response payload and verify state change
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        ReportJob resultJob = objectMapper.treeToValue(response.getPayload().getData(), ReportJob.class);
        assertNotNull(resultJob, "Response payload should contain a ReportJob entity");
        assertEquals("COMPLETED", resultJob.getStatus(), "ReportJob should be marked COMPLETED when Report found");
        assertNotNull(resultJob.getCompletedAt(), "CompletedAt should be set on successful completion");
        assertFalse(resultJob.getCompletedAt().isBlank(), "CompletedAt should be non-blank");

        // Verify EntityService was used to search for Reports
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Report.ENTITY_NAME), eq(Report.ENTITY_VERSION), any(), eq(true));
    }
}
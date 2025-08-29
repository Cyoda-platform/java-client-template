package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class StoreReportProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real implementations)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a job UUID for linking
        String jobTechnicalId = UUID.randomUUID().toString();
        UUID jobUuid = UUID.fromString(jobTechnicalId);

        // Stub addItem to return a technical UUID for persisted report
        when(entityService.addItem(eq(Report.ENTITY_NAME), eq(Report.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Prepare a valid ReportJob payload returned by getItem
        ReportJob job = new ReportJob();
        job.setName("MonthlyReportJob");
        job.setRequestedAt("2025-01-01T00:00:00Z");
        job.setRequestedBy("tester");
        job.setStatus("PENDING");
        DataPayload jobPayload = new DataPayload();
        jobPayload.setData(objectMapper.valueToTree(job));

        when(entityService.getItem(eq(jobUuid)))
                .thenReturn(CompletableFuture.completedFuture(jobPayload));

        // Stub updateItem to complete successfully
        when(entityService.updateItem(eq(jobUuid), any()))
                .thenReturn(CompletableFuture.completedFuture(jobUuid));

        // Instantiate processor with real serializerFactory, mocked entityService and real objectMapper
        StoreReportProcessor processor = new StoreReportProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Report entity JSON that passes Report.isValid()
        Report report = new Report();
        report.setName("Monthly Report");
        report.setReportId(UUID.randomUUID().toString());
        report.setJobTechnicalId(jobTechnicalId);
        report.setStatus("CREATING");
        report.setGeneratedAt("2025-01-01T01:00:00Z");
        report.setCreatedBy("tester");
        // Optional numeric fields left null

        JsonNode reportJson = objectMapper.valueToTree(report);

        // Build processor request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("StoreReportProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(reportJson);
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

        // Assert - basic success and payload present
        assertNotNull(response);
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        // Deserialize returned payload to Report and assert core happy-path expectations
        Report returned = objectMapper.treeToValue(response.getPayload().getData(), Report.class);
        assertNotNull(returned);
        // The processor should have persisted the report (we asserted addItem was called)
        // Ensure the returned report still contains the business reportId we provided
        assertEquals(report.getReportId(), returned.getReportId());
        // Ensure jobTechnicalId remains present
        assertEquals(report.getJobTechnicalId(), returned.getJobTechnicalId());

        // Verify that entityService interactions for the sunny path occurred:
        verify(entityService, atLeastOnce()).addItem(eq(Report.ENTITY_NAME), eq(Report.ENTITY_VERSION), any(Report.class));
        verify(entityService, atLeastOnce()).getItem(eq(jobUuid));
        verify(entityService, atLeastOnce()).updateItem(eq(jobUuid), any());
    }
}
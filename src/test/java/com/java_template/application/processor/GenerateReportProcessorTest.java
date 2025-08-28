package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class GenerateReportProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked per requirement (constructor requires it)
        EntityService entityService = mock(EntityService.class);

        GenerateReportProcessor processor = new GenerateReportProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid ReportJob entity that passes isValid()
        ReportJob job = new ReportJob();
        String jobId = "job-" + UUID.randomUUID();
        job.setJobId(jobId);
        job.setDataSourceUrl("http://example.com/data");
        job.setGeneratedAt(Instant.now().toString()); // required by isValid()
        job.setStatus("PENDING");
        job.setTriggerType("MANUAL");
        job.setRequestedMetrics("metricA,metricB");

        JsonNode entityJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("GenerateReportProcessor");
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
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned entity payload for expected sunny-day changes
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // Processor sets reportLocation to internal-reports/<jobId>.json
        assertEquals("internal-reports/" + jobId + ".json", out.get("reportLocation").asText());
        // Processor sets status to "REPORTING"
        assertEquals("REPORTING", out.get("status").asText());
        // Processor sets generatedAt to a non-empty timestamp
        assertNotNull(out.get("generatedAt").asText());
        assertFalse(out.get("generatedAt").asText().isBlank());
    }
}
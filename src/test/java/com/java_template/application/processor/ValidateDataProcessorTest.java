package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.datasource.version_1.DataSource;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ValidateDataProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare an existing DataSource that will be considered VALID by processor logic
        DataSource existingDs = new DataSource();
        existingDs.setId(UUID.randomUUID().toString());
        existingDs.setUrl("https://example.com/data.csv");
        existingDs.setSchema("price,area,other");
        existingDs.setSampleHash("samplehash");
        existingDs.setValidationStatus("UNKNOWN");
        existingDs.setLastFetchedAt(null);

        // Wrap into DataPayload as entityService would return
        DataPayload dsPayload = new DataPayload();
        dsPayload.setData(objectMapper.valueToTree(existingDs));
        // Note: do not call setId(...) because DataPayload does not expose setId(String) in the current API.

        when(entityService.getItemsByCondition(
                anyString(), anyInt(), any(), anyBoolean()
        )).thenReturn(CompletableFuture.completedFuture(List.of(dsPayload)));

        when(entityService.updateItem(any(UUID.class), any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Create processor under test
        ValidateDataProcessor processor = new ValidateDataProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid ReportJob that passes isValid()
        ReportJob job = new ReportJob();
        job.setJobId("job-1");
        job.setDataSourceUrl(existingDs.getUrl());
        job.setGeneratedAt("2025-01-01T00:00:00Z");
        job.setStatus("PENDING");
        job.setTriggerType("MANUAL");
        // optional fields can be left null

        JsonNode jobJson = objectMapper.valueToTree(job);
        DataPayload requestPayload = new DataPayload();
        requestPayload.setData(jobJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ValidateDataProcessor");
        request.setPayload(requestPayload);

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

        // Inspect returned payload to ensure status changed to ANALYZING (sunny path)
        assertNotNull(response.getPayload());
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertEquals("ANALYZING", out.get("status").asText());

        // Verify EntityService interactions occurred as expected
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(DataSource.ENTITY_NAME), eq(DataSource.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).updateItem(any(UUID.class), any());
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NotifySubscribersProcessorTest {

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

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a subscriber that matches the job's notifyFilters (frequency=weekly) and is ACTIVE
        Subscriber subscriber = new Subscriber();
        subscriber.setSubscriberId("sub-1");
        subscriber.setEmail("user@example.com");
        subscriber.setName("User One");
        subscriber.setFrequency("weekly");
        subscriber.setStatus("ACTIVE");
        subscriber.setFilters(null); // no additional filters

        DataPayload subscriberPayload = new DataPayload();
        subscriberPayload.setData(objectMapper.valueToTree(subscriber));

        List<DataPayload> subscriberList = List.of(subscriberPayload);
        when(entityService.getItemsByCondition(anyString(), any(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(subscriberList));

        // Build a valid ReportJob payload that passes isValid()
        ReportJob job = new ReportJob();
        job.setJobId("job-1");
        job.setDataSourceUrl("http://example.com/ds");
        job.setGeneratedAt("2025-01-01T00:00:00Z");
        job.setStatus("PENDING");
        job.setTriggerType("SCHEDULE");
        job.setNotifyFilters("frequency=weekly"); // ensure frequency filter present
        job.setReportLocation("http://example.com/report/job-1");

        DataPayload jobPayload = new DataPayload();
        jobPayload.setData(objectMapper.valueToTree(job));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("NotifySubscribersProcessor");
        request.setPayload(jobPayload);

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Instantiate processor with real serializerFactory, mocked entityService and real objectMapper
        NotifySubscribersProcessor processor = new NotifySubscribersProcessor(serializerFactory, entityService, objectMapper);

        // Act
        EntityProcessorCalculationResponse response = processor.process(ctx);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // The processor updates job.status to NOTIFYING in-memory then sets to COMPLETED if at least one send succeeded.
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());
        String resultingStatus = response.getPayload().getData().get("status").asText();
        assertEquals("COMPLETED", resultingStatus);
    }
}
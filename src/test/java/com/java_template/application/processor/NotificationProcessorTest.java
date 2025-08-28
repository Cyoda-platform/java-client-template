package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NotificationProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock EntityService (only mock allowed)
        EntityService entityService = mock(EntityService.class);

        // Prepare a valid Job entity (must satisfy Job.isValid())
        Job job = new Job();
        job.setJobId(UUID.randomUUID().toString());
        job.setSourceUrl("http://example.com");
        job.setState("COMPLETED");
        job.setScheduleAt("2025-01-01T00:00:00Z");
        job.setFailedCount(0);
        job.setSucceededCount(1);
        job.setTotalRecords(1);

        // Prepare a valid Subscriber to be returned by entityService.getItems(...)
        Subscriber.Channel channel = new Subscriber.Channel();
        channel.setAddress("mailto:someone@example.com");
        channel.setType("EMAIL");

        Subscriber subscriber = new Subscriber();
        subscriber.setSubscriberId(UUID.randomUUID().toString());
        subscriber.setName("Test Subscriber");
        subscriber.setActive(true);
        subscriber.setChannels(List.of(channel));
        // no filters -> will be notified

        // Create DataPayloads to return from entityService
        DataPayload subPayload = new DataPayload();
        JsonNode subNode = objectMapper.valueToTree(subscriber);
        subPayload.setData(subNode);
        List<DataPayload> subscribersList = new ArrayList<>();
        subscribersList.add(subPayload);

        // Stub entityService.getItems to return our subscriber list
        when(entityService.getItems(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(subscribersList));

        // Stub entityService.getItemsByCondition to return empty laureate list
        when(entityService.getItemsByCondition(any(), any(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(new ArrayList<>()));

        // Stub updateItem to succeed when NotificationProcessor attempts to update subscriber
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor under test
        NotificationProcessor processor = new NotificationProcessor(serializerFactory, entityService, objectMapper);

        // Build request payload using real serializer flow expectations
        JsonNode jobNode = objectMapper.valueToTree(job);
        DataPayload payload = new DataPayload();
        payload.setData(jobNode);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("NotificationProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(ctx);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload - should contain the same job id as input (sunny path)
        assertNotNull(response.getPayload());
        JsonNode out = (JsonNode) response.getPayload().getData();
        assertNotNull(out);
        assertEquals(job.getJobId(), out.get("jobId").asText());

        // Verify that entityService.updateItem was invoked to persist subscriber lastNotifiedAt
        verify(entityService, atLeastOnce()).updateItem(any(UUID.class), any());
    }
}
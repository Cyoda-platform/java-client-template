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

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NotifySubscribersProcessorTest {

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

        // Mock only EntityService as required
        EntityService entityService = mock(EntityService.class);

        // Prepare a single active email subscriber (so processor will simulate email and not call external HTTP)
        Subscriber subscriber = new Subscriber();
        subscriber.setId("sub-1");
        subscriber.setActive(Boolean.TRUE);
        subscriber.setContactType("email");
        subscriber.setContactDetail("user@example.com");
        subscriber.setCreatedAt("2025-01-01T00:00:00Z");

        DataPayload subscriberPayload = new DataPayload();
        subscriberPayload.setData(objectMapper.valueToTree(subscriber));

        when(entityService.getItems(
                eq(Subscriber.ENTITY_NAME),
                eq(Subscriber.ENTITY_VERSION),
                any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(List.of(subscriberPayload)));

        // Instantiate processor under test
        NotifySubscribersProcessor processor = new NotifySubscribersProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Job payload (fields required by isValid())
        Job job = new Job();
        job.setId("job-1");
        job.setRunTimestamp("2025-08-01T12:00:00Z");
        job.setSourceUrl("http://example.com/source");
        job.setState("RUNNING");

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("NotifySubscribersProcessor");
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

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        JsonNode out = response.getPayload().getData();
        // Processor should set state to NOTIFIED_SUBSCRIBERS and set completedTimestamp
        assertEquals("NOTIFIED_SUBSCRIBERS", out.path("state").asText());
        assertTrue(out.hasNonNull("completedTimestamp"));

        // Summary should be present and indicate zero failures in sunny path
        JsonNode summary = out.path("summary");
        assertTrue(summary.isObject());
        assertEquals(0, summary.path("failedCount").asInt());
        assertTrue(summary.path("errors").isArray());
    }
}
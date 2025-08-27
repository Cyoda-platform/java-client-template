package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NotifySubscribersProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        EntityService entityService = mock(EntityService.class);

        // Prepare a subscriber that will be returned by entityService.getItems
        Subscriber subscriber = new Subscriber();
        String subscriberTechnicalId = UUID.randomUUID().toString();
        subscriber.setId(subscriberTechnicalId);
        subscriber.setSubscriberId("sub-1");
        subscriber.setActive(Boolean.TRUE);
        subscriber.setContactType("EMAIL"); // EMAIL avoids actual HTTP call
        subscriber.setContactAddress("notify@example.com");
        subscriber.setFilters(null);

        ArrayNode subscribersArray = objectMapper.createArrayNode();
        subscribersArray.add(objectMapper.valueToTree(subscriber));

        when(entityService.getItems(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(subscribersArray));
        when(entityService.updateItem(eq(Subscriber.ENTITY_NAME), anyString(), any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(subscriberTechnicalId)));

        NotifySubscribersProcessor processor = new NotifySubscribersProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Job entity that will trigger notifications (state SUCCEEDED)
        Job job = new Job();
        job.setJobId("job-1");
        job.setState("SUCCEEDED");
        job.setRetryCount(0);
        job.setResultSummary("This run processed items in category Chemistry and other data.");
        // Ensure finishedAt is null so processor will set it
        job.setFinishedAt(null);

        // Convert entity to JsonNode using serializer
        JsonNode jobJson = processorSerializer.entityToJsonNode(job);

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

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // State should be transitioned to NOTIFIED_SUBSCRIBERS
        assertEquals("NOTIFIED_SUBSCRIBERS", out.path("state").asText());
        // finishedAt should be set (non-blank)
        String finishedAt = out.path("finishedAt").asText();
        assertNotNull(finishedAt);
        assertFalse(finishedAt.isBlank());

        // Verify that entityService.updateItem was invoked for the delivered subscriber
        verify(entityService, atLeastOnce()).updateItem(eq(Subscriber.ENTITY_NAME), anyString(), eq(UUID.fromString(subscriberTechnicalId)), any());
    }
}
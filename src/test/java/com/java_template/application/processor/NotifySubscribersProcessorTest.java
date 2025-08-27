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

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a valid Subscriber payload (active email subscriber)
        Subscriber subscriber = new Subscriber();
        subscriber.setId("sub-1");
        subscriber.setName("Test Subscriber");
        subscriber.setActive(true);
        subscriber.setContactType("email");
        subscriber.setContactDetails("test@example.com");

        DataPayload subscriberPayload = new DataPayload();
        subscriberPayload.setData(objectMapper.valueToTree(subscriber));

        when(entityService.getItems(anyString(), anyInt(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(subscriberPayload)));

        // Instantiate processor
        NotifySubscribersProcessor processor = new NotifySubscribersProcessor(serializerFactory, entityService, objectMapper);

        // Prepare a minimal valid Job payload
        Job job = new Job();
        job.setId("job-1");
        job.setState("COMPLETED");
        job.setSchedule("daily");
        // leave finishedAt null so processor will set it
        DataPayload jobPayload = new DataPayload();
        jobPayload.setData(objectMapper.valueToTree(job));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(job.getId());
        request.setProcessorName("NotifySubscribersProcessor");
        request.setPayload(jobPayload);

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

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // state should be updated to NOTIFIED_SUBSCRIBERS
        assertEquals("NOTIFIED_SUBSCRIBERS", out.get("state").asText());
        // subscribersNotifiedCount should be 1 (one active subscriber)
        assertEquals(1, out.get("subscribersNotifiedCount").asInt());
        // finishedAt should be set (non-blank)
        assertNotNull(out.get("finishedAt"));
        assertFalse(out.get("finishedAt").asText().isBlank());

        // Verify EntityService was called to fetch subscribers
        verify(entityService, atLeastOnce()).getItems(eq(Subscriber.ENTITY_NAME), eq(Subscriber.ENTITY_VERSION), any(), any(), any());
    }
}
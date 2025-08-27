package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.criterion.CriterionSerializer; // fallback import if present
import com.java_template.common.serializer.ProcessorSerializer; // ensure ProcessorSerializer available
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
        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a subscriber JSON record that will be returned by getItemsByCondition
        String technicalIdStr = UUID.randomUUID().toString();
        ObjectNode subscriberNode = objectMapper.createObjectNode();
        subscriberNode.put("technicalId", technicalIdStr);
        subscriberNode.put("subscriberId", "sub1");
        subscriberNode.put("active", true);
        subscriberNode.put("preferredPayload", "summary");
        ObjectNode contactMethods = objectMapper.createObjectNode();
        contactMethods.put("email", "notify@example.com");
        subscriberNode.set("contactMethods", contactMethods);

        ArrayNode results = objectMapper.createArrayNode();
        results.add(subscriberNode);

        when(entityService.getItemsByCondition(anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(results));

        when(entityService.updateItem(anyString(), anyString(), any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(technicalIdStr)));

        NotifySubscribersProcessor processor = new NotifySubscribersProcessor(serializerFactory, entityService, objectMapper);

        // Build Job payload that should pass isValid()
        ObjectNode jobNode = objectMapper.createObjectNode();
        jobNode.put("id", "job-1");
        jobNode.put("status", "PENDING");
        // subscribersSnapshot must contain the business id "sub1"
        ArrayNode subs = objectMapper.createArrayNode();
        subs.add("sub1");
        jobNode.set("subscribersSnapshot", subs);
        // ensure errorDetails exists as empty array to avoid NPE if accessed
        jobNode.set("errorDetails", objectMapper.createArrayNode());

        DataPayload payload = new DataPayload();
        payload.setData(jobNode);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("job-1");
        request.setProcessorName("NotifySubscribersProcessor");
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

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertEquals("NOTIFIED_SUBSCRIBERS", out.get("status").asText());
        assertTrue(out.get("notificationsSent").asBoolean());
        assertNotNull(out.get("finishedAt"));

        // Verify updateItem called to persist subscriber notification status
        verify(entityService, atLeastOnce()).updateItem(eq(Subscriber.ENTITY_NAME), anyString(), any(UUID.class), any());
    }
}
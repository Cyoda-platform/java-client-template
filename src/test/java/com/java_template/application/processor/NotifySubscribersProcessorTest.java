package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
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
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare laureates array (one laureate)
        ArrayNode laureatesArray = objectMapper.createArrayNode();
        ObjectNode laureate = objectMapper.createObjectNode();
        laureate.put("id", 1);
        laureate.put("firstname", "Ada");
        laureate.put("surname", "Lovelace");
        laureate.put("year", "1843");
        laureate.put("category", "computing");
        laureatesArray.add(laureate);

        // Prepare subscribers array (one active subscriber preferring summary, contactType=email to avoid webhook HTTP calls)
        ArrayNode subscribersArray = objectMapper.createArrayNode();
        ObjectNode subscriberNode = objectMapper.createObjectNode();
        subscriberNode.put("subscriberId", "sub-1");
        subscriberNode.put("active", true);
        subscriberNode.put("contactType", "email"); // will avoid HTTP call path
        ObjectNode contactDetails = objectMapper.createObjectNode();
        contactDetails.put("url", "http://example.invalid"); // not used for email
        subscriberNode.set("contactDetails", contactDetails);
        subscriberNode.put("preferredPayload", "summary");
        subscribersArray.add(subscriberNode);

        // Stub entityService to return laureates for Laureate entity and subscribers for Subscriber entity
        when(entityService.getItemsByCondition(eq(Laureate.ENTITY_NAME), anyString(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(laureatesArray));
        when(entityService.getItemsByCondition(eq(Subscriber.ENTITY_NAME), anyString(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(subscribersArray));

        NotifySubscribersProcessor processor = new NotifySubscribersProcessor(serializerFactory, entityService, objectMapper);

        // Build request payload matching Job structure minimally so Job.isValid() passes
        ObjectNode jobJson = objectMapper.createObjectNode();
        jobJson.put("jobId", "job-1");
        jobJson.put("state", "SUCCEEDED");
        // include minimal metadata map to allow modifications
        jobJson.set("metadata", objectMapper.createObjectNode());

        DataPayload payload = new DataPayload();
        payload.setData(jobJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
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
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        // Verify job state was updated to NOTIFIED_SUBSCRIBERS
        ObjectNode out = (ObjectNode) response.getPayload().getData();
        assertEquals("NOTIFIED_SUBSCRIBERS", out.get("state").asText());

        // Verify that entityService was used to fetch laureates and subscribers
        verify(entityService, times(1)).getItemsByCondition(eq(Laureate.ENTITY_NAME), anyString(), any(), anyBoolean());
        verify(entityService, times(1)).getItemsByCondition(eq(Subscriber.ENTITY_NAME), anyString(), any(), anyBoolean());
    }
}
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
import java.util.UUID;
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

        // Prepare a valid Subscriber that is active and verified and has contactDetails.url (required by isValid)
        Subscriber.ContactDetails contactDetails = new Subscriber.ContactDetails();
        contactDetails.setUrl("http://example.com/notify");

        Subscriber subscriber = new Subscriber();
        subscriber.setId(UUID.randomUUID().toString());
        subscriber.setActive(true);
        subscriber.setVerified(true);
        subscriber.setContactType("email"); // email path does not perform HTTP call
        subscriber.setCreatedAt("2020-01-01T00:00:00Z");
        subscriber.setContactDetails(contactDetails);

        JsonNode subscriberJson = objectMapper.valueToTree(subscriber);
        DataPayload subscriberPayload = new DataPayload();
        subscriberPayload.setData(subscriberJson);

        // Stub getItems to return the single subscriber payload
        when(entityService.getItems(eq(Subscriber.ENTITY_NAME), eq(Subscriber.ENTITY_VERSION), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(subscriberPayload)));

        // Stub updateItem to succeed when processor updates lastNotifiedAt
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        NotifySubscribersProcessor processor = new NotifySubscribersProcessor(serializerFactory, entityService, objectMapper);

        // Prepare a valid Job payload that will trigger notifications (notifyOn = BOTH => shouldNotify = true)
        Job job = new Job();
        job.setJobId(UUID.randomUUID().toString());
        job.setSourceUrl("http://example.com/source");
        job.setSchedule("ON_DEMAND");
        job.setNotifyOn("BOTH");
        job.setStatus("SUCCEEDED"); // any non-blank status is fine when notifyOn = BOTH

        JsonNode jobJson = objectMapper.valueToTree(job);
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

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertEquals("NOTIFIED_SUBSCRIBERS", out.get("status").asText());
        // ingestResult should be present (processor initializes it)
        assertNotNull(out.get("ingestResult"));
    }
}
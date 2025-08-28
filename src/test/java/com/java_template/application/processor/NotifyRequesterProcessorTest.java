package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NotifyRequesterProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked per requirements (constructor requires it)
        EntityService entityService = mock(EntityService.class);

        NotifyRequesterProcessor processor = new NotifyRequesterProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid AdoptionRequest entity for the sunny REJECTED path
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setId("ar-1");
        adoptionRequest.setPetId("not-a-uuid"); // intentionally not a UUID to avoid entityService.getItem call
        adoptionRequest.setRequesterName("Jane Doe");
        adoptionRequest.setContactEmail("jane.doe@example.com"); // required by isValid()
        adoptionRequest.setContactPhone(null); // leave phone null so SMS path is skipped
        adoptionRequest.setMotivation("I love pets");
        adoptionRequest.setNotes(""); // empty string to avoid NPE in processor when appending notes
        adoptionRequest.setStatus("REJECTED"); // processor only notifies on REJECTED
        adoptionRequest.setSubmittedAt(Instant.now().toString());

        JsonNode entityJson = objectMapper.valueToTree(adoptionRequest);

        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("NotifyRequesterProcessor");
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

        assertNotNull(response.getPayload());
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);

        // The processor appends a notification attempt record into notes on the happy path
        String notes = out.has("notes") && !out.get("notes").isNull() ? out.get("notes").asText() : null;
        assertNotNull(notes);
        assertTrue(notes.contains("Notification attempt"), "Expected notes to contain a notification attempt record");
        // The processor will at least have attempted email notification (may have recorded an error),
        // so assert presence of the word "email" in the notes.
        assertTrue(notes.toLowerCase().contains("email"), "Expected notes to mention email notification");

        // Verify we did not attempt to fetch pet because we used an invalid UUID string
        verify(entityService, never()).getItem(any());
    }
}
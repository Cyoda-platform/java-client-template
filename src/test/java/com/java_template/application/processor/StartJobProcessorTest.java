package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StartJobProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real objects)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Prepare a WeeklySendJob that passes isValid() prior to processing.
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String createdAt = now.minusHours(1).toString();
        String originalRunAt = now.minusHours(1).toString();
        String scheduledForPast = now.minusMinutes(5).toString(); // in the past => should start job

        WeeklySendJob job = new WeeklySendJob();
        job.setCatFactTechnicalId("some-technical-id");
        job.setCreatedAt(createdAt);
        job.setRunAt(originalRunAt); // required by isValid() before processing
        job.setScheduledFor(scheduledForPast);
        job.setStatus("CREATED");
        job.setErrorMessage(null);

        JsonNode entityJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("StartJobProcessor");
        DataPayload payload = new DataPayload();
        // DataPayload in examples exposes setData; use it here
        payload.setData(entityJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Create processor (no EntityService required)
        StartJobProcessor processor = new StartJobProcessor(serializerFactory);

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess(), "Processor should succeed on sunny path");

        assertNotNull(response.getPayload(), "Response should contain a payload");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Payload data must be present");

        // Core expected changes: status set to RUNNING, runAt updated, errorMessage cleared
        assertEquals("RUNNING", responseData.get("status").asText());
        assertTrue(responseData.has("runAt"));
        String updatedRunAt = responseData.get("runAt").asText();
        assertNotNull(updatedRunAt);
        assertFalse(updatedRunAt.isBlank());
        assertNotEquals(originalRunAt, updatedRunAt, "runAt should be updated to current time");
        // errorMessage should be null (or missing) after successful start
        JsonNode errorNode = responseData.get("errorMessage");
        if (errorNode != null && !errorNode.isNull()) {
            fail("errorMessage should be null after successful start");
        }
        // scheduledFor should remain unchanged
        assertEquals(scheduledForPast, responseData.get("scheduledFor").asText());
    }
}
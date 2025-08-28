package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CloseRequestProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real Jackson serializers and serializer factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        CloseRequestProcessor processor = new CloseRequestProcessor(serializerFactory);

        // Build a valid AdoptionRequest entity that represents the sunny path.
        AdoptionRequest entity = new AdoptionRequest();
        entity.setRequestId("req-123");
        entity.setPetId("pet-456");
        entity.setUserId("user-789");
        entity.setAdoptionFee(25.0);
        entity.setHomeVisitRequired(Boolean.FALSE);
        entity.setPaymentStatus("PAID");
        entity.setStatus("REJECTED"); // initial status that should be transitioned to CLOSED
        entity.setRequestedAt("2020-01-01T00:00:00Z");
        entity.setNotes(null);

        // Convert entity to JsonNode for payload
        com.fasterxml.jackson.databind.JsonNode entityJson = objectMapper.valueToTree(entity);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("CloseRequestProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert - basic success and payload modifications (status changed to CLOSED and notes appended)
        assertNotNull(response);
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        AdoptionRequest result = objectMapper.treeToValue(response.getPayload().getData(), AdoptionRequest.class);
        assertNotNull(result);
        assertEquals("CLOSED", result.getStatus());
        assertNotNull(result.getNotes());
        assertTrue(result.getNotes().contains("Closed by CloseRequestProcessor"));
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.catfact.version_1.CatFact;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class ValidateCatFactProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real ObjectMapper configured as required
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked per instructions
        EntityService entityService = mock(EntityService.class);

        ValidateCatFactProcessor processor = new ValidateCatFactProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid CatFact instance (must satisfy CatFact.isValid())
        CatFact catFact = new CatFact();
        catFact.setTechnicalId("tech-1");
        catFact.setText("Cats are awesome.");
        catFact.setSource("catfact.ninja");
        catFact.setFetchedAt("2025-09-07T09:00:01Z");
        catFact.setSendCount(0);
        catFact.setEngagementScore(1.5);
        catFact.setValidationStatus("PENDING");

        JsonNode entityJson = objectMapper.valueToTree(catFact);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ValidateCatFactProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
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

        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData);
        // Core sunny-day behavior: validationStatus should be set to VALID
        assertEquals("VALID", responseData.get("validationStatus").asText());
        // Ensure original text preserved
        assertEquals("Cats are awesome.", responseData.get("text").asText());
    }
}
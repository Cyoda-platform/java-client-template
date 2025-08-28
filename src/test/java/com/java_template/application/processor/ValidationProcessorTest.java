package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationProcessorTest {

    @Test
    void sunnyDay_validation_process_test() throws Exception {
        // Arrange - real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        ValidationProcessor processor = new ValidationProcessor(serializerFactory);

        // Build a valid Laureate entity that passes isValid() and triggers enrichment:
        // - born and year allow computation of ageAtAward
        // - bornCountryCode contains mixed-case/whitespace to test normalization
        Laureate laureate = new Laureate();
        laureate.setId(853);
        laureate.setFirstname("Akira");
        laureate.setSurname("Suzuki");
        laureate.setCategory("Chemistry");
        laureate.setYear("1975");
        laureate.setBorn("1950-01-01"); // will allow computed age = 25
        laureate.setBornCountryCode(" jp ");
        laureate.setAgeAtAward(null); // let processor compute it

        // Serialize entity to JsonNode payload
        JsonNode entityJson = objectMapper.valueToTree(laureate);
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId(UUID.randomUUID().toString());
        request.setRequestId(UUID.randomUUID().toString());
        request.setEntityId("e1");
        request.setProcessorName("ValidationProcessor");
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "Output data must be present");

        // ageAtAward should be computed to 25 (1975 - 1950)
        assertTrue(out.has("ageAtAward"), "Output should contain ageAtAward");
        assertEquals(25, out.get("ageAtAward").intValue());

        // normalizedCountryCode should be set to "JP"
        assertTrue(out.has("normalizedCountryCode"), "Output should contain normalizedCountryCode");
        assertEquals("JP", out.get("normalizedCountryCode").asText());

        // lastUpdatedAt should be set (non-empty)
        assertTrue(out.hasNonNull("lastUpdatedAt"), "Output should contain lastUpdatedAt");
        assertFalse(out.get("lastUpdatedAt").asText().isBlank(), "lastUpdatedAt should not be blank");
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class EnrichmentProcessorTest {

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

        // Only EntityService may be mocked per requirements (constructor requires it)
        EntityService entityService = mock(EntityService.class);

        EnrichmentProcessor processor = new EnrichmentProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Laureate entity that satisfies isValid()
        Laureate laureate = new Laureate();
        laureate.setId(853); // required
        laureate.setFirstname("Akira");
        laureate.setSurname("Suzuki");
        laureate.setCategory("Chemistry");
        laureate.setYear("2010"); // used to compute age
        laureate.setBorn("1940-09-12"); // used to compute age -> 70
        laureate.setBornCountryCode("jp"); // should normalize to "JP"

        JsonNode entityJson = objectMapper.valueToTree(laureate);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId(UUID.randomUUID().toString());
        request.setEntityId("e1");
        request.setProcessorName("EnrichmentProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
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

        // ageAtAward expected to be 70 (2010 - 1940)
        assertTrue(out.has("ageAtAward"));
        assertEquals(70, out.get("ageAtAward").asInt());

        // normalizedCountryCode expected to be upper-cased two-letter code "JP"
        assertTrue(out.has("normalizedCountryCode"));
        assertEquals("JP", out.get("normalizedCountryCode").asText());

        // lastUpdatedAt should be set (non-blank ISO-like string)
        assertTrue(out.has("lastUpdatedAt"));
        String lastUpdated = out.get("lastUpdatedAt").asText();
        assertNotNull(lastUpdated);
        assertFalse(lastUpdated.isBlank());
    }
}
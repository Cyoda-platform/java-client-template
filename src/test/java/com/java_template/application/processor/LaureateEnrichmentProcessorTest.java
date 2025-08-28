package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.CriterionSerializer;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class LaureateEnrichmentProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - ObjectMapper + serializers + factory (real objects)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked (required by processor constructor)
        EntityService entityService = mock(EntityService.class);

        LaureateEnrichmentProcessor processor = new LaureateEnrichmentProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Laureate entity that will pass isValid() and trigger enrichments
        Laureate laureate = new Laureate();
        laureate.setId("laureate-1");
        laureate.setFirstname("First");
        laureate.setSurname("Last");
        laureate.setCategory("Physics");
        laureate.setYear("2000");
        laureate.setBorn("1970-01-01");
        laureate.setBornCountry("Japan"); // should normalize to "JP"
        // Note: do not set ageAtAward or normalizedCountryCode - processor should compute them

        JsonNode entityJson = objectMapper.valueToTree(laureate);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("LaureateEnrichmentProcessor");
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should be successful");

        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response data should not be null");

        // ageAtAward computed: 2000 - 1970 = 30
        assertTrue(responseData.has("ageAtAward"), "ageAtAward should be present in the response data");
        assertEquals(30, responseData.get("ageAtAward").asInt(), "ageAtAward should be 30");

        // normalizedCountryCode computed from bornCountry "Japan" -> "JP"
        assertTrue(responseData.has("normalizedCountryCode"), "normalizedCountryCode should be present in the response data");
        assertEquals("JP", responseData.get("normalizedCountryCode").asText(), "normalizedCountryCode should be JP");
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import com.java_template.common.workflow.CyodaEventContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FieldFormatCriterionTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and SerializerFactory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        FieldFormatCriterion processor = new FieldFormatCriterion(serializerFactory);

        // Build a valid Laureate entity that passes isValid() and the format checks
        Laureate laureate = new Laureate();
        laureate.setId("L-1");
        laureate.setFirstname("Alfred");
        laureate.setSurname("Nobel");
        laureate.setCategory("chemistry");
        laureate.setYear("2000"); // 4-digit year in acceptable range
        laureate.setBorn("1833-10-21"); // valid date format
        laureate.setDied("1896-12-10"); // valid date format
        laureate.setBornCountryCode("se"); // lower-case two-letter code -> should normalize to "SE"
        laureate.setGender("male");

        JsonNode entityJson = objectMapper.valueToTree(laureate);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("FieldFormatCriterion");
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

        // Assert core happy-path results
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should be successful");

        // Inspect returned payload data for expected sunny-day state changes
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData, "Returned data node should not be null");

        // normalizedCountryCode should be upper-cased "SE" and validated set to "VALIDATED"
        assertEquals("SE", returnedData.path("normalizedCountryCode").asText(), "Country code should be normalized to upper-case");
        assertEquals("VALIDATED", returnedData.path("validated").asText(), "Entity should be marked as VALIDATED");
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PersistLaureateProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        PersistLaureateProcessor processor = new PersistLaureateProcessor(serializerFactory);

        ObjectNode entityJson = objectMapper.createObjectNode();
        // Provide minimal fields expected by Laureate.isValid() and processor logic
        entityJson.put("id", 1);
        entityJson.put("firstname", " John ");
        entityJson.put("surname", " Doe ");
        entityJson.put("born", "1970-01-01"); // ISO date to compute derived age
        entityJson.put("year", "2000"); // award year
        entityJson.put("bornCountryCode", " se "); // will be normalized to "SE"

        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PersistLaureateProcessor");
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

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // Trimmed names
        assertEquals("John", out.get("firstname").asText());
        assertEquals("Doe", out.get("surname").asText());
        // Derived age: 2000 - 1970 = 30
        assertEquals(30, out.get("derivedAgeAtAward").asInt());
        // Normalized country code upper-cased
        assertEquals("SE", out.get("normalizedCountryCode").asText());
    }
}
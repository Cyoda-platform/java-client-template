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

public class EnrichmentProcessorTest {

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

        EnrichmentProcessor processor = new EnrichmentProcessor(serializerFactory);

        // Build payload that satisfies Laureate.isValid() and triggers enrichment logic
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", 1);
        data.put("firstname", "Marie");
        data.put("surname", "Curie");
        data.put("category", "physics");
        data.put("year", "1903");
        data.put("validationStatus", "validated");
        // born used to compute ageAtAward (1903 - 1867 = 36)
        data.put("born", "1867-11-07");
        // bornCountryCode to test normalization to uppercase
        data.put("bornCountryCode", "pl");

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("EnrichmentProcessor");
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
        // ageAtAward should be computed: 1903 - 1867 = 36
        assertEquals(36, out.get("ageAtAward").asInt());
        // normalizedCountryCode should be uppercase of bornCountryCode
        assertEquals("PL", out.get("normalizedCountryCode").asText());
        // lastSeenAt should be set to a non-blank timestamp string
        assertNotNull(out.get("lastSeenAt"));
        assertFalse(out.get("lastSeenAt").asText().isBlank());
    }
}
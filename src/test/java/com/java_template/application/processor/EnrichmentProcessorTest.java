package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

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

        // EntityService required by constructor - mock only this dependency
        EntityService entityService = mock(EntityService.class);

        EnrichmentProcessor processor = new EnrichmentProcessor(serializerFactory, entityService, objectMapper);

        // Build a minimal valid Laureate payload that triggers derived_ageAtAward and country normalization
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", 1);
        data.put("firstname", "Albert");
        data.put("surname", "Einstein");
        data.put("category", "Physics");
        data.put("year", "1921");
        data.put("born", "1879-03-14");
        data.put("borncountry", "Sweden"); // should normalize to "SE" via Locale matching

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("EnrichmentProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(ctx);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        // derived_ageAtAward = awardYear - birthYear = 1921 - 1879 = 42
        assertTrue(out.has("derived_ageAtAward"));
        assertEquals(42, out.get("derived_ageAtAward").intValue());
        // normalizedCountryCode should be set to an ISO country (for "Sweden" -> "SE")
        assertTrue(out.has("normalizedCountryCode"));
        assertEquals("SE", out.get("normalizedCountryCode").asText());
    }
}
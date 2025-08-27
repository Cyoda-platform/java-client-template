package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MarkForSummaryProcessorTest {

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

        // No EntityService required by constructor
        MarkForSummaryProcessor processor = new MarkForSummaryProcessor(serializerFactory);

        // Build payload that satisfies Laureate.isValid(): id, firstname, surname, category, year, validationStatus
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", 123); // integer id
        data.put("firstname", "Marie");
        data.put("surname", "Curie");
        data.put("category", "Physics");
        data.put("year", "1903");
        data.put("validationStatus", "VALID");

        // Provide additional fields to trigger ageAtAward and normalizedCountryCode logic
        data.put("born", "1867-11-07");
        data.put("bornCountryCode", "pl"); // should be normalized to "PL"

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId(UUID.randomUUID().toString());
        request.setRequestId(UUID.randomUUID().toString());
        request.setEntityId("123");
        request.setProcessorName("MarkForSummaryProcessor");
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

        // ageAtAward should be computed: year 1903 - birth year 1867 = 36
        assertTrue(out.has("ageAtAward"));
        assertEquals(36, out.get("ageAtAward").asInt());

        // normalizedCountryCode should be set to upper-case bornCountryCode
        assertTrue(out.has("normalizedCountryCode"));
        assertEquals("PL", out.get("normalizedCountryCode").asText());

        // lastSeenAt should be set (non-blank string)
        assertTrue(out.has("lastSeenAt"));
        String lastSeen = out.get("lastSeenAt").asText();
        assertNotNull(lastSeen);
        assertFalse(lastSeen.isBlank());
    }
}
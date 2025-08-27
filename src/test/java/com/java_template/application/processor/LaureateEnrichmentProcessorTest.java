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

public class LaureateEnrichmentProcessorTest {

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

        LaureateEnrichmentProcessor processor = new LaureateEnrichmentProcessor(serializerFactory);

        // Build a payload that should pass entity.isValid() and exercise enrichment logic:
        // born -> "1970-05-20", year -> "2000" => derivedAgeAtAward = 30
        // bornCountryCode -> " usa " => normalizedCountryCode = "US"
        // firstname / surname have extra spaces to verify trimming
        ObjectNode entityJson = objectMapper.createObjectNode();
        entityJson.put("id", 1);
        entityJson.put("firstname", " John ");
        entityJson.put("surname", " Doe ");
        entityJson.put("born", "1970-05-20");
        entityJson.put("year", "2000");
        entityJson.put("bornCountryCode", " usa ");
        entityJson.put("bornCountry", " United States ");
        entityJson.put("affiliationName", " Univ ");
        entityJson.put("affiliationCity", " City ");
        entityJson.put("affiliationCountry", " Country ");

        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("LaureateEnrichmentProcessor");
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

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);

        // Core happy-path expectations:
        // derived age computed as 30
        assertTrue(out.has("derivedAgeAtAward"));
        assertEquals(30, out.get("derivedAgeAtAward").asInt());

        // country code normalized to "US"
        assertTrue(out.has("normalizedCountryCode"));
        assertEquals("US", out.get("normalizedCountryCode").asText());

        // strings trimmed
        assertEquals("John", out.get("firstname").asText());
        assertEquals("Doe", out.get("surname").asText());

        // year trimmed and preserved
        assertEquals("2000", out.get("year").asText());
    }
}
package com.java_template.application.processor;

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

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LaureateValidationProcessorTest {

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

        LaureateValidationProcessor processor = new LaureateValidationProcessor(serializerFactory);

        // Prepare payload that satisfies isValid checks:
        // - id > 0
        // - year parseable and within range
        // - born valid ISO date
        // Include some fields that will be trimmed/normalized by the processor.
        ObjectNode entityJson = objectMapper.createObjectNode();
        entityJson.put("id", 100);
        entityJson.put("year", "2000");
        entityJson.put("firstname", " John ");
        entityJson.put("surname", " Doe ");
        entityJson.put("born", "1970-01-01");
        entityJson.put("bornCountryCode", "se"); // will be uppercased to "SE" and normalizedCountryCode set

        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("LaureateValidationProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
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
        EntityProcessorCalculationResponse response = processor.process(ctx);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        ObjectNode out = (ObjectNode) response.getPayload().getData();

        // bornCountryCode should be uppercased
        assertEquals("SE", out.get("bornCountryCode").asText());

        // normalizedCountryCode should be set to the uppercase bornCountryCode
        assertEquals("SE", out.get("normalizedCountryCode").asText());

        // derivedAgeAtAward should be computed: year(2000) - born year(1970) = 30
        assertTrue(out.has("derivedAgeAtAward"));
        assertEquals(30, out.get("derivedAgeAtAward").asInt());
    }
}
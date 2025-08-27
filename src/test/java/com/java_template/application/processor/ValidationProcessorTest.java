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

import static org.junit.jupiter.api.Assertions.*;

public class ValidationProcessorTest {

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

        ValidationProcessor processor = new ValidationProcessor(serializerFactory);

        // Build payload that satisfies Laureate.isValid() and will exercise trimming/uppercase logic
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", 123);
        data.put("firstname", " John ");
        data.put("surname", " Doe ");
        data.put("motivation", " For outstanding work ");
        data.put("category", " Physics ");
        data.put("year", "2020");
        data.put("born", " 1900-01-01 ");
        data.put("bornCity", " Stockholm ");
        data.put("bornCountry", " Sweden ");
        data.put("bornCountryCode", "se");
        data.put("persistedAt", "2020-01-01T00:00:00Z");
        data.put("recordStatus", "NEW");

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ValidationProcessor");
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
        // Verify core happy-path changes: trimming firstname/surname and uppercasing bornCountryCode
        assertEquals("John", out.get("firstname").asText());
        assertEquals("Doe", out.get("surname").asText());
        assertEquals("SE", out.get("bornCountryCode").asText());

        // Also ensure required fields still present
        assertEquals(123, out.get("id").asInt());
        assertEquals("NEW", out.get("recordStatus").asText());
        assertEquals("2020-01-01T00:00:00Z", out.get("persistedAt").asText());
    }
}
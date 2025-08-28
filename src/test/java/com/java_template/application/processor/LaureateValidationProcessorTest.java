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

        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", 1);
        // include whitespace to verify trimming behavior
        data.put("firstname", " John ");
        data.put("surname", "Doe ");
        data.put("category", "Physics");
        data.put("year", "2000");
        data.put("ingestJobId", "job-1");
        // born present to compute age (2000 - 1970 = 30)
        data.put("born", "1970-05-05");
        // borncountry present and no borncountrycode => expect mapping to "JP"
        data.put("borncountry", "Japan");

        DataPayload payload = new DataPayload();
        payload.setData(data);

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

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);

        // computedAge should be 30 (2000 - 1970)
        assertTrue(out.has("computedAge"));
        assertEquals(30, out.get("computedAge").intValue());

        // borncountrycode should be mapped to JP
        assertTrue(out.has("borncountrycode"));
        assertEquals("JP", out.get("borncountrycode").asText());

        // firstname and surname should be trimmed
        assertEquals("John", out.get("firstname").asText());
        assertEquals("Doe", out.get("surname").asText());
    }
}
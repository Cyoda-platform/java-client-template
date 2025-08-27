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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class EnrichLaureateProcessorTest {

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

        EnrichLaureateProcessor processor = new EnrichLaureateProcessor(serializerFactory);

        // Build payload that satisfies entity.isValid() and exercises processor logic:
        // - born and awardYear present to compute ageAtAward
        // - borncountry present to infer borncountrycode ("japan" -> "JP")
        // - firstname/surname with surrounding whitespace to verify trimming
        ObjectNode data = objectMapper.createObjectNode();
        data.put("laureateId", "L-123");
        data.put("born", "1930-09-12");
        data.put("awardYear", "1965");
        data.put("borncountry", "Japan");
        data.put("firstname", " John ");
        data.put("surname", " Doe ");
        // initial processingStatus absent/null

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId(UUID.randomUUID().toString());
        request.setRequestId(UUID.randomUUID().toString());
        request.setEntityId("e-" + UUID.randomUUID());
        request.setProcessorName("EnrichLaureateProcessor");
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should be successful");

        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode out = response.getPayload().getData();
        assertNotNull(out, "Output data should not be null");

        // Verify core happy-path transformations
        assertEquals("ENRICHED", out.path("processingStatus").asText(), "processingStatus should be ENRICHED");
        assertEquals("JP", out.path("borncountrycode").asText(), "borncountrycode should be inferred to JP");
        // Age: 1965 - 1930 = 35
        assertTrue(out.has("ageAtAward"), "ageAtAward should be present");
        assertEquals(35, out.path("ageAtAward").asInt(), "ageAtAward should be correctly computed");

        // Trimmed names
        assertEquals("John", out.path("firstname").asText(), "firstname should be trimmed");
        assertEquals("Doe", out.path("surname").asText(), "surname should be trimmed");
    }
}
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

        // Processor requires EntityService in constructor; mock it per rules
        EntityService entityService = mock(EntityService.class);

        LaureateEnrichmentProcessor processor = new LaureateEnrichmentProcessor(serializerFactory, entityService, objectMapper);

        // Build minimal payload that satisfies Laureate.isValid()
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", 1);
        data.put("firstname", "Alice");
        data.put("surname", "Smith");
        data.put("category", "Physics");
        data.put("year", "2000");
        data.put("ingestJobId", "job-1");
        // Include born and borncountry to allow enrichment (computedAge and derived country code)
        data.put("born", "1970-01-01");
        data.put("borncountry", "Sweden");

        DataPayload payload = new DataPayload();
        payload.setData(data);

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

        // computedAge: born 1970-01-01 and award year 2000 -> age at end of award year = 30
        assertTrue(out.has("computedAge"));
        assertEquals(30, out.get("computedAge").asInt());

        // borncountry "Sweden" should derive to "SE"
        assertTrue(out.has("borncountrycode"));
        assertEquals("SE", out.get("borncountrycode").asText());
    }
}
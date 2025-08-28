package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.datasource.version_1.DataSource;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RecordSourceProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        RecordSourceProcessor processor = new RecordSourceProcessor(serializerFactory);

        // Build a minimal valid DataSource for the processor's pre-validation (requires id and url)
        DataSource ds = new DataSource();
        ds.setId("ds-" + UUID.randomUUID());
        // Use a well-known reachable URL for sunny-path fetch
        ds.setUrl("https://example.com");

        JsonNode entityJson = objectMapper.valueToTree(ds);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(ds.getId());
        request.setProcessorName("RecordSourceProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
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

        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        JsonNode out = response.getPayload().getData();

        // Processor should have attempted to fetch and set these fields on success
        assertNotNull(out.get("validationStatus"));
        assertEquals("FETCHED", out.get("validationStatus").asText());

        assertNotNull(out.get("sampleHash"));
        assertTrue(!out.get("sampleHash").asText().isBlank());

        assertNotNull(out.get("schema"));
        assertTrue(!out.get("schema").asText().isBlank());
    }
}
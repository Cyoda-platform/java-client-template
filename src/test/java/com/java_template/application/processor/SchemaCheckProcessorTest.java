package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.datasource.version_1.DataSource;
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

public class SchemaCheckProcessorTest {

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

        // Only EntityService may be mocked per requirements
        EntityService entityService = mock(EntityService.class);

        SchemaCheckProcessor processor = new SchemaCheckProcessor(serializerFactory, entityService, objectMapper);

        // Prepare a valid DataSource entity that will pass isValid() before processing
        DataSource ds = new DataSource();
        ds.setId("ds-1");
        ds.setUrl("http://example.com/data.csv");
        // Provide a schema that contains required columns: price, area, bedrooms
        ds.setSchema("[\"price\",\"area\",\"bedrooms\",\"other\"]");
        ds.setSampleHash("samplehash123");
        // validationStatus must be non-blank for isValid() to pass; processor will update it
        ds.setValidationStatus("UNKNOWN");

        JsonNode entityJson = objectMapper.valueToTree(ds);
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(ds.getId());
        request.setProcessorName("SchemaCheckProcessor");
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
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);

        // Processor should set validationStatus to VALID for a schema containing required columns
        assertEquals("VALID", out.get("validationStatus").asText());

        // Processor should set lastFetchedAt to a non-blank timestamp string
        assertTrue(out.hasNonNull("lastFetchedAt"));
        String lastFetchedAt = out.get("lastFetchedAt").asText();
        assertNotNull(lastFetchedAt);
        assertFalse(lastFetchedAt.isBlank());
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class ValidateProductProcessorTest {

    @Test
    void sunnyDay_validateProductProcessor_process() throws Exception {
        // Setup real ObjectMapper as required
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory (no Spring)
        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked (required by constructor); not used in sunny path here
        EntityService entityService = mock(EntityService.class);

        // Instantiate processor with real serializers and objectMapper
        ValidateProductProcessor processor = new ValidateProductProcessor(serializerFactory, objectMapper, entityService);

        // Prepare a valid Product entity that will exercise normalization and metadata enrichment
        Product product = new Product();
        product.setProductId("prod-123");
        product.setName("Test Product");
        // Price with >2 decimal places to test rounding
        product.setPrice(12.345);
        // No category so processor should infer from metadata.tags
        product.setCategory(null);
        // Provide metadata containing tags so category is inferred to "Food"
        product.setMetadata("{\"tags\":[\"Food\"]}");

        // Convert product to JsonNode for payload
        JsonNode entityJson = objectMapper.valueToTree(product);

        // Build request and payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(UUID.randomUUID().toString());
        request.setProcessorName("ValidateProductProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext implementation
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

        // Assert basic response
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        // Inspect returned payload data for expected sunny-day changes
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response data should not be null");

        // Price should be normalized to 2 decimals (12.345 -> 12.35)
        assertEquals(12.35, responseData.get("price").asDouble(), 0.000001);

        // Category should have been inferred from metadata.tags -> "Food"
        assertEquals("Food", responseData.get("category").asText());

        // Metadata field is a JSON string; parse and check validationStatus is READY
        String metadataString = responseData.get("metadata").asText();
        JsonNode metadataNode = objectMapper.readTree(metadataString);
        assertEquals("READY", metadataNode.get("validationStatus").asText());
    }
}
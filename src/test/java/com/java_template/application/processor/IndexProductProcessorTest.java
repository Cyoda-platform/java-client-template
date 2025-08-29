package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.product.version_1.Product;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class IndexProductProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper setup using real Jackson serializers (ignore unknown props)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked per policy (processor doesn't require specific stubbing for sunny path)
        EntityService entityService = mock(EntityService.class);

        // Create the processor using real serializerFactory and objectMapper and mocked EntityService
        IndexProductProcessor processor = new IndexProductProcessor(serializerFactory, entityService, objectMapper);

        // Build a minimal valid Product that will pass isValid()
        Product product = new Product();
        product.setProductId("prod-123");
        product.setName("  Apple  "); // has surrounding whitespace to test trimming
        product.setCategory("fresh fruit"); // will be normalized to Title Case "Fresh Fruit"
        product.setPrice(1.234); // will be rounded to 2 decimals -> 1.23
        // metadata left null to test metadata creation path

        JsonNode entityJson = objectMapper.valueToTree(product);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("IndexProductProcessor");
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        assertNotNull(response.getPayload(), "Response payload must be present");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response data must be present");

        // Convert returned data to Product to inspect enriched fields
        Product result = objectMapper.treeToValue(responseData, Product.class);
        assertNotNull(result);

        // Name trimmed
        assertEquals("Apple", result.getName(), "Name should be trimmed");

        // Category normalized to Title Case
        assertEquals("Fresh Fruit", result.getCategory(), "Category should be normalized to Title Case");

        // Price rounded to 2 decimals
        assertEquals(1.23, result.getPrice(), 0.0001, "Price should be rounded to 2 decimal places");

        // Metadata should have been set and contain validation = VALID
        assertNotNull(result.getMetadata(), "Metadata should be set");
        JsonNode metaNode = objectMapper.readTree(result.getMetadata());
        assertTrue(metaNode.has("validation"), "Metadata should contain a validation field");
        assertEquals("VALID", metaNode.get("validation").asText(), "Entity should be marked VALID in metadata");

        // Also ensure indexing timestamp exists
        assertTrue(metaNode.has("indexedAt"), "Metadata should contain indexedAt timestamp");
    }
}
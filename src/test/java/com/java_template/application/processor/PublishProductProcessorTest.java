package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class PublishProductProcessorTest {

    @Test
    void sunnyDay_publishProduct_process() {
        // Setup real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService may be mocked (processor requires it in constructor)
        EntityService entityService = mock(EntityService.class);

        // Create processor with real serializers and mocked service
        PublishProductProcessor processor = new PublishProductProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Product entity that passes isValid() and isValidEntity checks
        Product product = new Product();
        product.setName("  My Product  "); // will be trimmed by processor
        product.setSku(" sku-123 ");       // will be trimmed and upper-cased by processor
        product.setPrice(19.99);
        product.setQuantityAvailable(10);
        product.setCategory(" electronics "); // will be trimmed

        // Convert to JsonNode payload
        JsonNode productJson = objectMapper.valueToTree(product);

        // Build request and payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PublishProductProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(productJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext
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

        // Assert core sunny-day expectations
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        // Inspect response payload data for expected transformations
        assertNotNull(response.getPayload(), "Response payload should be present");
        JsonNode dataNode = response.getPayload().getData();
        assertNotNull(dataNode, "Response payload data should be present");

        // SKU should be trimmed and upper-cased
        assertEquals("SKU-123", dataNode.get("sku").asText(), "SKU should be upper-cased and trimmed");

        // Name should be trimmed
        assertEquals("My Product", dataNode.get("name").asText(), "Name should be trimmed");

        // Category should be trimmed
        assertEquals("electronics", dataNode.get("category").asText(), "Category should be trimmed");

        // Events should contain a ProductCreated event with payload.sku equal to SKU
        JsonNode eventsNode = dataNode.get("events");
        assertNotNull(eventsNode, "Events array should be present");
        assertTrue(eventsNode.isArray() && eventsNode.size() > 0, "Events should contain at least one entry");

        boolean foundCreated = false;
        for (JsonNode ev : eventsNode) {
            JsonNode typeNode = ev.get("type");
            if (typeNode != null && "ProductCreated".equals(typeNode.asText())) {
                JsonNode evPayload = ev.get("payload");
                if (evPayload != null && evPayload.get("sku") != null &&
                        "SKU-123".equals(evPayload.get("sku").asText())) {
                    foundCreated = true;
                    break;
                }
            }
        }
        assertTrue(foundCreated, "Should have added a ProductCreated event with payload.sku equal to the product SKU");
    }
}
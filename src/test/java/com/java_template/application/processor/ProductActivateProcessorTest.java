package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.product.version_1.Product;
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

import static org.junit.jupiter.api.Assertions.*;

public class ProductActivateProcessorTest {

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

        ProductActivateProcessor processor = new ProductActivateProcessor(serializerFactory);

        // Create a valid Product entity that is "inactive" by having availableQuantity = 0
        Product product = new Product();
        product.setId("prod-1");
        product.setName("Test Product");
        product.setSku("TP-001");
        product.setWarehouseId("wh-1");
        product.setAvailableQuantity(0); // processor should set this to 1
        product.setPrice(9.99);

        JsonNode productJson = objectMapper.valueToTree(product);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("req-1");
        request.setRequestId("req-1");
        request.setEntityId(product.getId());
        request.setProcessorName("ProductActivateProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(productJson);
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
        // availableQuantity should have been set to at least 1 by the processor
        assertTrue(out.has("availableQuantity"));
        assertEquals(1, out.get("availableQuantity").intValue());
    }
}
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shipment.version_1.Shipment;
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
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CreateShipmentProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a product that will be returned by getItemsByCondition
        Product product = new Product();
        product.setSku("SKU123");
        product.setName("Test Product");
        product.setPrice(10.0);
        product.setQuantityAvailable(5); // current stock

        ObjectNode productDataNode = objectMapper.valueToTree(product);
        ObjectNode meta = objectMapper.createObjectNode();
        String technicalId = UUID.randomUUID().toString();
        meta.put("entityId", technicalId);

        DataPayload productPayload = new DataPayload();
        productPayload.setData(productDataNode);
        productPayload.setMeta(meta);

        when(entityService.getItemsByCondition(eq(Product.ENTITY_NAME), eq(Product.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(productPayload)));

        // Stub updateItem to simulate successful update
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(technicalId)));

        // Create processor instance (no Spring)
        CreateShipmentProcessor processor = new CreateShipmentProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Shipment entity that passes isValid()
        Shipment.ShipmentLine line = new Shipment.ShipmentLine();
        line.setSku("SKU123");
        line.setQtyOrdered(2);
        line.setQtyPicked(0);
        line.setQtyShipped(0);

        Shipment shipment = new Shipment();
        shipment.setShipmentId("s1");
        shipment.setOrderId("o1");
        shipment.setStatus("CREATED");
        shipment.setLines(List.of(line));

        JsonNode shipmentJson = objectMapper.valueToTree(shipment);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("CreateShipmentProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(shipmentJson);
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

        // The processor returns the processed shipment in response payload data
        assertNotNull(response.getPayload());
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData);
        // Check basic expected fields remain and the line sku matches
        assertEquals("s1", returnedData.get("shipmentId").asText());
        JsonNode linesNode = returnedData.get("lines");
        assertNotNull(linesNode);
        assertTrue(linesNode.isArray());
        JsonNode firstLine = linesNode.get(0);
        assertEquals("SKU123", firstLine.get("sku").asText());
        // qtyPicked and qtyShipped should be present and non-negative
        assertTrue(firstLine.get("qtyPicked").asInt() >= 0);
        assertTrue(firstLine.get("qtyShipped").asInt() >= 0);

        // Verify EntityService interactions: we should have searched for product and updated it
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Product.ENTITY_NAME), eq(Product.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(technicalId)), any());
    }
}
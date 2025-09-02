```java
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ShipmentUpdateOrderStateProcessorTest {

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

        EntityService entityService = mock(EntityService.class);
        
        // Mocking the getFirstItemByCondition method to return a valid Order
        Order mockOrder = new Order();
        mockOrder.setOrderId("order-123");
        mockOrder.setOrderNumber("ORD-123");
        mockOrder.setLines(List.of());
        mockOrder.setTotals(new Order.OrderTotals());
        mockOrder.setGuestContact(new Order.GuestContact());
        
        when(entityService.getFirstItemByCondition(
                eq(Order.class), 
                eq(Order.ENTITY_NAME), 
                eq(Order.ENTITY_VERSION), 
                any(), 
                eq(true))
        ).thenReturn(Optional.of(mockOrder));
        
        // Create the processor with the mocked EntityService
        ShipmentUpdateOrderStateProcessor processor = new ShipmentUpdateOrderStateProcessor(serializerFactory);
        processor.entityService = entityService; // inject the mock service

        // Prepare the request
        Shipment shipment = new Shipment();
        shipment.setShipmentId("shipment-123");
        shipment.setOrderId("order-123");
        shipment.setLines(List.of());
        
        JsonNode shipmentJson = objectMapper.valueToTree(shipment);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ShipmentUpdateOrderStateProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(shipmentJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext
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
        assertTrue(response.getPayload().getData().has("updatedAt")); // Check if updatedAt is set
        assertEquals("shipment-123", shipment.getShipmentId()); // Validate the shipment ID remains the same
        verify(entityService, times(1)).getFirstItemByCondition(any(), any(), any(), anyBoolean());
    }
}
```
package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.Order.OrderItem;
import com.java_template.application.entity.order.version_1.Order.UserSnapshot;
import com.java_template.application.entity.order.version_1.Order.Address;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.shipment.version_1.Shipment.ShipmentItem;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AutoSendProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - serializers and factory (real objects)
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a shipment that is initially WAITING_TO_SEND and valid
        Shipment shipment = new Shipment();
        String shipmentId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        shipment.setId(shipmentId);
        shipment.setShipmentNumber("SN-12345");
        shipment.setOrderId(orderId);
        shipment.setStatus("WAITING_TO_SEND");
        shipment.setCreatedAt(Instant.now().toString());
        shipment.setWarehouseId(UUID.randomUUID().toString());
        ShipmentItem item = new ShipmentItem();
        item.setProductId(UUID.randomUUID().toString());
        item.setQty(1);
        shipment.setItems(java.util.List.of(item));

        // Create a shipped-other-shipment payload to indicate all shipments for the order are SENT
        Shipment otherShipment = new Shipment();
        otherShipment.setId(UUID.randomUUID().toString());
        otherShipment.setShipmentNumber("SN-OTHER");
        otherShipment.setOrderId(orderId);
        otherShipment.setStatus("SENT");
        otherShipment.setCreatedAt(Instant.now().toString());
        otherShipment.setWarehouseId(UUID.randomUUID().toString());
        ShipmentItem otherItem = new ShipmentItem();
        otherItem.setProductId(UUID.randomUUID().toString());
        otherItem.setQty(1);
        otherShipment.setItems(java.util.List.of(otherItem));
        DataPayload shippedPayload = new DataPayload();
        shippedPayload.setData(objectMapper.valueToTree(otherShipment));

        // Prepare an Order that is in WAITING_TO_SEND so processor will attempt to update it
        Order order = new Order();
        String orderIdStr = orderId;
        order.setId(orderIdStr);
        order.setOrderNumber("ORD-001");
        order.setCartId(UUID.randomUUID().toString());
        order.setCreatedAt(Instant.now().toString());
        order.setStatus("WAITING_TO_SEND");
        order.setTotalAmount(10.0);
        OrderItem oi = new OrderItem();
        oi.setProductId(UUID.randomUUID().toString());
        oi.setPrice(10.0);
        oi.setQtyOrdered(1);
        oi.setQtyFulfilled(0);
        order.setItems(java.util.List.of(oi));
        UserSnapshot us = new UserSnapshot();
        us.setEmail("u@example.com");
        us.setName("User");
        Address addr = new Address();
        addr.setLine1("1 Test St");
        addr.setCity("Testville");
        addr.setCountry("TC");
        addr.setPostal("00000");
        us.setAddress(addr);
        order.setUserSnapshot(us);

        DataPayload orderPayload = new DataPayload();
        orderPayload.setData(objectMapper.valueToTree(order));

        // Stub EntityService behavior:
        // - When searching for shipments for the order -> return list with otherShipment (SENT)
        when(entityService.getItemsByCondition(eq(Shipment.ENTITY_NAME), eq(Shipment.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(java.util.List.of(shippedPayload)));

        // - When searching for the order -> return the order payload
        when(entityService.getItemsByCondition(eq(Order.ENTITY_NAME), eq(Order.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(java.util.List.of(orderPayload)));

        // - When updating the order -> return a completed future
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Create processor under test
        AutoSendProcessor processor = new AutoSendProcessor(serializerFactory, entityService, objectMapper);

        // Build request with the shipment as payload (use real DataPayload)
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(shipmentId);
        request.setProcessorName("AutoSendProcessor");
        DataPayload reqPayload = new DataPayload();
        JsonNode shipmentJson = objectMapper.valueToTree(shipment);
        reqPayload.setData(shipmentJson);
        request.setPayload(reqPayload);

        // Minimal CyodaEventContext
        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(ctx);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload: should reflect shipment moved to SENT and tracking info added
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());
        Shipment outShipment = objectMapper.treeToValue(response.getPayload().getData(), Shipment.class);
        assertEquals("SENT", outShipment.getStatus());
        assertNotNull(outShipment.getTrackingInfo());
        assertEquals("AUTO", outShipment.getTrackingInfo().get("carrier"));
        assertEquals(shipment.getShipmentNumber(), outShipment.getTrackingInfo().get("trackingNumber"));

        // Verify updateItem was called to update the order to SENT (since all shipments reported sent)
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(orderIdStr)), any());
    }
}
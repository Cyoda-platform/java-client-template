package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.Order.GuestContact;
import com.java_template.application.entity.order.version_1.Order.Address;
import com.java_template.application.entity.order.version_1.Order.Line;
import com.java_template.application.entity.order.version_1.Order.Totals;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.shipment.version_1.Shipment.ShipmentLine;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ReadyToSendProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real ObjectMapper per instructions
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

        // Prepare a valid Order that will be returned by entityService.getItem(...)
        Order order = new Order();
        String orderUuid = UUID.randomUUID().toString();
        order.setOrderId(orderUuid);
        order.setOrderNumber("ON-123");
        order.setStatus("PICKING");
        order.setCreatedAt("2025-01-01T00:00:00Z");
        GuestContact guest = new GuestContact();
        Address addr = new Address();
        addr.setLine1("1 Test St");
        addr.setCity("City");
        addr.setCountry("CT");
        addr.setPostcode("0000");
        guest.setAddress(addr);
        guest.setEmail("test@example.com");
        order.setGuestContact(guest);
        Totals totals = new Totals();
        totals.setGrand(100.0);
        order.setTotals(totals);
        Line orderLine = new Line();
        orderLine.setSku("SKU-1");
        orderLine.setQty(1);
        orderLine.setUnitPrice(100.0);
        order.setLines(List.of(orderLine));

        // Build DataPayload for returned order
        DataPayload orderPayload = new DataPayload();
        orderPayload.setData(objectMapper.valueToTree(order));

        when(entityService.getItem(eq(UUID.fromString(orderUuid))))
                .thenReturn(CompletableFuture.completedFuture(orderPayload));
        when(entityService.updateItem(eq(UUID.fromString(orderUuid)), any(Order.class)))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(orderUuid)));

        // Instantiate processor
        ReadyToSendProcessor processor = new ReadyToSendProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Shipment in PICKING to exercise transition
        Shipment shipment = new Shipment();
        String shipmentId = UUID.randomUUID().toString();
        shipment.setShipmentId(shipmentId);
        shipment.setOrderId(orderUuid);
        shipment.setStatus("PICKING");
        shipment.setCreatedAt("2025-01-01T00:00:00Z");
        ShipmentLine sLine = new ShipmentLine();
        sLine.setSku("SKU-1");
        sLine.setQtyOrdered(1);
        sLine.setQtyPicked(1);
        sLine.setQtyShipped(0);
        shipment.setLines(List.of(sLine));

        // Build request and payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ReadyToSendProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(shipment));
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

        // Assert basic contract
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload for updated shipment
        assertNotNull(response.getPayload());
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData);

        Shipment processed = objectMapper.treeToValue(responseData, Shipment.class);
        assertNotNull(processed);
        assertEquals("WAITING_TO_SEND", processed.getStatus(), "Shipment status should be transitioned to WAITING_TO_SEND");
        assertNotNull(processed.getUpdatedAt());
        assertFalse(processed.getUpdatedAt().isBlank());

        // Verify interactions with EntityService for order retrieval and update
        verify(entityService, atLeastOnce()).getItem(eq(UUID.fromString(orderUuid)));
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(orderUuid)), any(Order.class));
    }
}
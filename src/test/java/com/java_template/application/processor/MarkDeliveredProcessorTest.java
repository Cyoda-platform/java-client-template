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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MarkDeliveredProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare an Order that is valid per Order.isValid()
        String orderUuidStr = UUID.randomUUID().toString();
        Order order = new Order();
        order.setOrderId(orderUuidStr);
        order.setOrderNumber("ORDER-123");
        order.setStatus("SHIPPED");
        order.setCreatedAt("2025-01-01T00:00:00Z");
        order.setUpdatedAt("2025-01-02T00:00:00Z");
        GuestContact guestContact = new GuestContact();
        Address address = new Address();
        address.setCity("City");
        address.setCountry("Country");
        address.setLine1("Street 1");
        address.setPostcode("12345");
        guestContact.setAddress(address);
        guestContact.setEmail("guest@example.com");
        order.setGuestContact(guestContact);
        Totals totals = new Totals();
        totals.setGrand(100.0);
        order.setTotals(totals);
        Line orderLine = new Line();
        orderLine.setSku("SKU-1");
        orderLine.setUnitPrice(50.0);
        orderLine.setQty(2);
        order.setLines(List.of(orderLine));

        DataPayload orderPayload = new DataPayload();
        orderPayload.setData(objectMapper.valueToTree(order));

        when(entityService.getItem(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(orderPayload));
        when(entityService.updateItem(any(UUID.class), any())).thenReturn(CompletableFuture.completedFuture(UUID.fromString(orderUuidStr)));

        // Build a Shipment that is valid and will be processed
        Shipment shipment = new Shipment();
        String shipmentId = "SHIP-1";
        shipment.setShipmentId(shipmentId);
        shipment.setOrderId(orderUuidStr);
        shipment.setStatus("IN_TRANSIT");
        shipment.setCreatedAt("2025-01-01T01:00:00Z");
        shipment.setUpdatedAt("2025-01-02T02:00:00Z");
        ShipmentLine line = new ShipmentLine();
        line.setSku("SKU-1");
        line.setQtyOrdered(2);
        line.setQtyPicked(2);
        line.setQtyShipped(1); // less than ordered; processor should set to 2
        shipment.setLines(List.of(line));

        JsonNode shipmentJson = objectMapper.valueToTree(shipment);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("MarkDeliveredProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(shipmentJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Instantiate processor (no Spring)
        MarkDeliveredProcessor processor = new MarkDeliveredProcessor(serializerFactory, entityService, objectMapper);

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess(), "Processor should report success");

        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        JsonNode resultNode = response.getPayload().getData();
        assertEquals("DELIVERED", resultNode.get("status").asText(), "Shipment status should be DELIVERED after processing");

        JsonNode linesNode = resultNode.get("lines");
        assertNotNull(linesNode);
        assertTrue(linesNode.isArray());
        assertEquals(1, linesNode.size());
        JsonNode firstLine = linesNode.get(0);
        assertEquals(2, firstLine.get("qtyShipped").asInt(), "qtyShipped should be set to qtyOrdered (2)");

        // Verify EntityService updateItem was called to update the order status
        verify(entityService, atLeastOnce()).updateItem(any(UUID.class), any());
    }
}
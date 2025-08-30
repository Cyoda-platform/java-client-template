package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.shipment.version_1.Shipment.ShipmentLine;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MarkSentActionTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real ObjectMapper configured per instructions
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only the EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare an Order payload that will be returned by entityService.getItemsByCondition(...)
        Order order = new Order();
        order.setOrderId(UUID.randomUUID().toString());
        order.setOrderNumber("ON-123");
        order.setStatus("PROCESSING");
        order.setCreatedAt(Instant.now().toString());

        Order.GuestContact guest = new Order.GuestContact();
        Order.Address addr = new Order.Address();
        addr.setCity("City");
        addr.setCountry("Country");
        addr.setLine1("Line1");
        addr.setPostcode("12345");
        guest.setAddress(addr);
        guest.setEmail("test@example.com");
        guest.setName("Tester");
        order.setGuestContact(guest);

        Order.Totals totals = new Order.Totals();
        totals.setGrand(100.0);
        order.setTotals(totals);

        Order.Line orderLine = new Order.Line();
        orderLine.setSku("SKU-1");
        orderLine.setUnitPrice(100.0);
        orderLine.setQty(1);
        order.setLines(List.of(orderLine));

        // Prepare DataPayload for the Order with meta containing technical entityId
        String technicalOrderId = UUID.randomUUID().toString();
        DataPayload orderPayload = new DataPayload();
        orderPayload.setData(objectMapper.valueToTree(order));
        orderPayload.setMeta(objectMapper.createObjectNode().put("entityId", technicalOrderId));

        // Stub getItemsByCondition to return the order payload
        when(entityService.getItemsByCondition(
                eq(Order.ENTITY_NAME),
                eq(Order.ENTITY_VERSION),
                any(),
                eq(true)
        )).thenReturn(CompletableFuture.completedFuture(List.of(orderPayload)));

        // Stub updateItem to succeed
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(technicalOrderId)));

        // Instantiate the processor under test (no Spring)
        MarkSentAction processor = new MarkSentAction(serializerFactory, entityService, objectMapper);

        // Build a valid Shipment entity payload that passes Shipment.isValid()
        Shipment shipment = new Shipment();
        shipment.setShipmentId(UUID.randomUUID().toString());
        shipment.setOrderId(order.getOrderId()); // reference to order above
        shipment.setStatus("PICKED");
        shipment.setCreatedAt(Instant.now().toString());

        ShipmentLine line = new ShipmentLine();
        line.setSku("SKU-1");
        line.setQtyOrdered(1);
        line.setQtyPicked(1);
        line.setQtyShipped(0); // will be updated by processor
        shipment.setLines(List.of(line));

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("MarkSentAction");
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(shipment));
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload and ensure shipment status set to SENT and qtyShipped updated
        assertNotNull(response.getPayload());
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData);

        Shipment returnedShipment = objectMapper.treeToValue(returnedData, Shipment.class);
        assertEquals("SENT", returnedShipment.getStatus(), "Shipment status should be set to SENT by processor");
        assertNotNull(returnedShipment.getLines());
        assertFalse(returnedShipment.getLines().isEmpty());
        ShipmentLine returnedLine = returnedShipment.getLines().get(0);
        assertEquals(returnedLine.getQtyPicked(), returnedLine.getQtyShipped(), "qtyShipped should be set to qtyPicked");

        // Verify that the processor attempted to update the related Order via EntityService.updateItem
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(technicalOrderId)), any());
    }
}
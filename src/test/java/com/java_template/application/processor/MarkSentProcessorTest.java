package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
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

public class MarkSentProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real objects)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare an Order that will be returned by entityService.getItem(...) and that passes validation
        String orderUuid = UUID.randomUUID().toString();
        Order order = new Order();
        order.setOrderId(orderUuid);
        order.setOrderNumber("ORD-123");
        order.setStatus("PROCESSING");
        order.setCreatedAt("2025-01-01T00:00:00Z");

        GuestContact guestContact = new GuestContact();
        Address address = new Address();
        address.setLine1("1 Test St");
        address.setCity("Testville");
        address.setCountry("Testland");
        address.setPostcode("TST1");
        guestContact.setAddress(address);
        guestContact.setEmail("test@example.com");
        guestContact.setName("Tester");
        order.setGuestContact(guestContact);

        Totals totals = new Totals();
        totals.setGrand(100.0);
        order.setTotals(totals);

        Line orderLine = new Line();
        orderLine.setSku("SKU-1");
        orderLine.setUnitPrice(100.0);
        orderLine.setQty(1);
        order.setLines(List.of(orderLine));

        // Prepare DataPayload to be returned for getItem
        DataPayload orderPayload = new DataPayload();
        orderPayload.setData(objectMapper.valueToTree(order));

        // Stub EntityService.getItem(...) called by processor to retrieve the related Order
        when(entityService.getItem(any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(orderPayload));

        // Stub EntityService.updateItem(...) to simulate successful persistence
        when(entityService.updateItem(any(UUID.class), any())).thenReturn(CompletableFuture.completedFuture(UUID.fromString(orderUuid)));

        // Create processor (no Spring)
        MarkSentProcessor processor = new MarkSentProcessor(serializerFactory, entityService, objectMapper);

        // Prepare a valid Shipment that will pass Shipment.isValid()
        String shipmentUuid = UUID.randomUUID().toString();
        Shipment shipment = new Shipment();
        shipment.setShipmentId(shipmentUuid);
        shipment.setOrderId(orderUuid);
        shipment.setStatus("PENDING");
        ShipmentLine line = new ShipmentLine();
        line.setSku("SKU-1");
        line.setQtyOrdered(1);
        line.setQtyPicked(0);
        line.setQtyShipped(0);
        shipment.setLines(List.of(line));
        shipment.setCreatedAt("2025-01-02T00:00:00Z");

        // Build request with payload containing the Shipment JSON
        JsonNode shipmentJson = objectMapper.valueToTree(shipment);
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("MarkSentProcessor");
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

        // Assert - basic sunny-day checks
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload - shipment status should have been set to SENT by the processor
        assertNotNull(response.getPayload());
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData);
        assertEquals("SENT", returnedData.get("status").asText());

        // Verify that entityService was used to fetch and update the Order
        verify(entityService, atLeastOnce()).getItem(any(UUID.class));
        verify(entityService, atLeastOnce()).updateItem(any(UUID.class), any());
    }
}
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PickCompleteProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Configure real ObjectMapper as required
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory (no Spring)
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare a valid Shipment that is in PICKING state (so processor will transition it)
        Shipment.ShipmentLine line = new Shipment.ShipmentLine();
        line.setSku("SKU-123");
        line.setQtyOrdered(1);
        line.setQtyPicked(0);
        line.setQtyShipped(0);

        Shipment shipment = new Shipment();
        shipment.setShipmentId(UUID.randomUUID().toString());
        shipment.setOrderId(UUID.randomUUID().toString());
        shipment.setStatus("PICKING");
        shipment.setCreatedAt(Instant.now().toString());
        shipment.setLines(List.of(line));

        JsonNode shipmentJson = objectMapper.valueToTree(shipment);

        // Prepare a valid Order that will be returned by EntityService (so processor will update it)
        Order.Line orderLine = new Order.Line();
        orderLine.setSku("SKU-123");
        orderLine.setQty(1);
        orderLine.setUnitPrice(10.0);
        orderLine.setLineTotal(10.0);

        Order.Totals totals = new Order.Totals();
        totals.setGrand(10.0);
        totals.setItems(10.0);

        Order.GuestContact.Address address = new Order.GuestContact.Address();
        address.setLine1("1 Test St");
        address.setCity("Testville");
        address.setCountry("TS");
        address.setPostcode("T1 1ST");

        Order.GuestContact guest = new Order.GuestContact();
        guest.setAddress(address);
        guest.setEmail("test@example.com");
        guest.setName("Test");

        Order order = new Order();
        order.setOrderId(UUID.randomUUID().toString());
        order.setOrderNumber("ORD-1");
        order.setStatus("PICKING");
        order.setCreatedAt(Instant.now().toString());
        order.setGuestContact(guest);
        order.setTotals(totals);
        order.setLines(List.of(orderLine));

        JsonNode orderJson = objectMapper.valueToTree(order);

        // Prepare DataPayload for returned Order with meta.entityId to allow update call
        String technicalId = UUID.randomUUID().toString();
        DataPayload orderPayload = new DataPayload();
        orderPayload.setData(orderJson);
        // create meta with entityId
        orderPayload.setMeta(objectMapper.createObjectNode().put("entityId", technicalId));

        // Stub EntityService.getItemsByCondition to return the order payload
        when(entityService.getItemsByCondition(eq(Order.ENTITY_NAME), eq(Order.ENTITY_VERSION), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(List.of(orderPayload)));

        // Stub EntityService.updateItem to succeed
        when(entityService.updateItem(eq(UUID.fromString(technicalId)), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(technicalId)));

        // Build request with shipment payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PickCompleteProcessor");
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

        // Instantiate processor with real serializers and mocked entityService
        PickCompleteProcessor processor = new PickCompleteProcessor(serializerFactory, entityService, objectMapper);

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert basic response success
        assertNotNull(response);
        assertTrue(response.getSuccess(), "Processor should report success in sunny path");

        // Extract returned Shipment from response payload and assert transition applied
        assertNotNull(response.getPayload());
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData);

        Shipment returnedShipment = objectMapper.treeToValue(returnedData, Shipment.class);
        assertNotNull(returnedShipment);
        assertEquals("WAITING_TO_SEND", returnedShipment.getStatus(), "Shipment should have transitioned to WAITING_TO_SEND");
        assertNotNull(returnedShipment.getUpdatedAt(), "Shipment.updatedAt should be set");
        assertFalse(returnedShipment.getUpdatedAt().isBlank(), "Shipment.updatedAt should not be blank");

        // Verify that EntityService was used to lookup and update the Order
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Order.ENTITY_NAME), eq(Order.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(technicalId)), any(Order.class));
    }
}
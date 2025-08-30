package com.java_template.application.processor;

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

public class MarkDeliveredActionTest {

    @Test
    void sunnyDay_markDelivered_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare an Order that will be returned by EntityService (must be valid)
        Order order = new Order();
        UUID orderUuid = UUID.randomUUID();
        order.setOrderId(orderUuid.toString());
        order.setOrderNumber("ON-123");
        order.setStatus("PROCESSING");
        order.setCreatedAt(Instant.now().toString());
        // minimal guest contact
        Order.GuestContact gc = new Order.GuestContact();
        Order.Address addr = new Order.Address();
        addr.setLine1("123 Test St");
        addr.setCity("Testville");
        addr.setCountry("Testland");
        addr.setPostcode("TST1");
        gc.setAddress(addr);
        gc.setEmail("test@example.com");
        gc.setName("Test User");
        order.setGuestContact(gc);
        // totals
        Order.Totals totals = new Order.Totals();
        totals.setGrand(100.0);
        order.setTotals(totals);
        // lines
        Order.Line line = new Order.Line();
        line.setSku("SKU-1");
        line.setUnitPrice(100.0);
        line.setQty(1);
        order.setLines(List.of(line));

        // Return payload for getItem(...) call
        DataPayload orderPayload = new DataPayload();
        orderPayload.setData(objectMapper.valueToTree(order));
        when(entityService.getItem(any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(orderPayload));
        // Stub updateItem to succeed
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(orderUuid));

        // Prepare Shipment that is in SENT state and valid
        Shipment shipment = new Shipment();
        shipment.setShipmentId(UUID.randomUUID().toString());
        shipment.setOrderId(orderUuid.toString());
        shipment.setStatus("SENT");
        shipment.setCreatedAt(Instant.now().toString());
        Shipment.ShipmentLine sLine = new Shipment.ShipmentLine();
        sLine.setSku("SKU-1");
        sLine.setQtyOrdered(5);
        sLine.setQtyPicked(1);
        sLine.setQtyShipped(0);
        shipment.setLines(List.of(sLine));

        // Build request with payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("MarkDeliveredAction");
        DataPayload requestPayload = new DataPayload();
        requestPayload.setData(objectMapper.valueToTree(shipment));
        request.setPayload(requestPayload);

        // Minimal CyodaEventContext
        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Instantiate processor with real serializers and mocked EntityService
        MarkDeliveredAction processor = new MarkDeliveredAction(serializerFactory, entityService, objectMapper);

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect payload: shipment status should be DELIVERED and updatedAt should be set
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());
        String status = response.getPayload().getData().get("status").asText();
        assertEquals("DELIVERED", status);
        assertTrue(response.getPayload().getData().hasNonNull("updatedAt"));
        // Verify updateItem on Order was attempted
        verify(entityService, atLeastOnce()).updateItem(eq(orderUuid), any());
    }
}
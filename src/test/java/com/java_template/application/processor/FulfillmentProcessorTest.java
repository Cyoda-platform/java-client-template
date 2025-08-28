package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pickledger.version_1.PickLedger;
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

public class FulfillmentProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Only EntityService is mocked per instructions
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(anyString(), any(), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(UUID.randomUUID()));

        FulfillmentProcessor processor = new FulfillmentProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Shipment entity (must pass isValid)
        Shipment.ShipmentItem item = new Shipment.ShipmentItem();
        item.setProductId("product-1");
        item.setQty(2);

        Shipment shipment = new Shipment();
        shipment.setId(UUID.randomUUID().toString());
        shipment.setShipmentNumber("S-100");
        shipment.setOrderId(UUID.randomUUID().toString());
        shipment.setStatus("PENDING_PICK"); // sunny path begins here and should transition
        shipment.setCreatedAt(Instant.now().toString());
        shipment.setWarehouseId("WH-1");
        shipment.setItems(List.of(item));

        // Prepare request and payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(shipment.getId());
        request.setProcessorName("FulfillmentProcessor");
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

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Response payload should contain the shipment entity with an advanced status (not the original PENDING_PICK)
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());

        // Deserialize output to Shipment to inspect
        Shipment out = objectMapper.treeToValue(response.getPayload().getData(), Shipment.class);
        assertNotNull(out);
        // Core sunny-day expectation: shipment must have progressed from PENDING_PICK (either to PICKING or WAITING_TO_SEND)
        assertNotEquals("PENDING_PICK", out.getStatus());

        // Ensure picks were attempted (entityService.addItem should have been called at least once for PickLedger)
        verify(entityService, atLeastOnce()).addItem(eq(PickLedger.ENTITY_NAME), eq(PickLedger.ENTITY_VERSION), any());
    }
}
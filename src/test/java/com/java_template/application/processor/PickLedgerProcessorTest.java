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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PickLedgerProcessorTest {

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

        // Mock only EntityService as required
        EntityService entityService = mock(EntityService.class);

        // Prepare a shipment id
        String shipmentId = UUID.randomUUID().toString();

        // Prepare a PickLedger entity that is valid and already audited (to avoid randomness)
        PickLedger pick = new PickLedger();
        pick.setId(UUID.randomUUID().toString());
        pick.setOrderId(UUID.randomUUID().toString());
        pick.setProductId(UUID.randomUUID().toString());
        pick.setShipmentId(shipmentId);
        pick.setTimestamp(Instant.now().toString());
        pick.setQtyRequested(10);
        pick.setQtyPicked(5);
        pick.setAuditStatus("AUDIT_PASSED"); // ensure deterministic sunny-path

        // Create payload for the incoming request
        DataPayload incomingPayload = new DataPayload();
        incomingPayload.setData(objectMapper.valueToTree(pick));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(pick.getId());
        request.setProcessorName("PickLedgerProcessor");
        request.setPayload(incomingPayload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Prepare getItemsByCondition to return a list of PickLedger payloads all with AUDIT_PASSED
        PickLedger otherPick = new PickLedger();
        otherPick.setId(UUID.randomUUID().toString());
        otherPick.setOrderId(UUID.randomUUID().toString());
        otherPick.setProductId(UUID.randomUUID().toString());
        otherPick.setShipmentId(shipmentId);
        otherPick.setTimestamp(Instant.now().toString());
        otherPick.setQtyRequested(2);
        otherPick.setQtyPicked(2);
        otherPick.setAuditStatus("AUDIT_PASSED");

        DataPayload otherPickPayload = new DataPayload();
        otherPickPayload.setData(objectMapper.valueToTree(otherPick));

        List<DataPayload> pickList = new ArrayList<>();
        pickList.add(otherPickPayload);
        // include the incoming pick as well to simulate full dataset
        DataPayload incomingPickPayload = new DataPayload();
        incomingPickPayload.setData(objectMapper.valueToTree(pick));
        pickList.add(incomingPickPayload);

        when(entityService.getItemsByCondition(eq(PickLedger.ENTITY_NAME), eq(PickLedger.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(pickList));

        // Prepare a Shipment that is not yet PICKED
        Shipment shipment = new Shipment();
        shipment.setId(shipmentId);
        shipment.setShipmentNumber("S-001");
        shipment.setOrderId(UUID.randomUUID().toString());
        shipment.setStatus("READY");
        shipment.setCreatedAt(Instant.now().toString());
        shipment.setWarehouseId(UUID.randomUUID().toString());
        shipment.setItems(List.of());
        DataPayload shipmentPayload = new DataPayload();
        shipmentPayload.setData(objectMapper.valueToTree(shipment));

        when(entityService.getItem(eq(UUID.fromString(shipmentId))))
                .thenReturn(CompletableFuture.completedFuture(shipmentPayload));

        // Stub updateItem to complete successfully
        when(entityService.updateItem(eq(UUID.fromString(shipmentId)), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(shipmentId)));

        // Instantiate processor under test
        PickLedgerProcessor processor = new PickLedgerProcessor(serializerFactory, entityService, objectMapper);

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Deserialize returned entity and assert audit status remains PASSED (sunny path)
        DataPayload respPayload = response.getPayload();
        assertNotNull(respPayload);
        PickLedger result = objectMapper.treeToValue(respPayload.getData(), PickLedger.class);
        assertNotNull(result);
        assertEquals("AUDIT_PASSED", result.getAuditStatus());

        // Verify that shipment was loaded and update attempted to mark it as PICKED
        verify(entityService, atLeastOnce()).getItem(eq(UUID.fromString(shipmentId)));
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(shipmentId)), any());
    }
}
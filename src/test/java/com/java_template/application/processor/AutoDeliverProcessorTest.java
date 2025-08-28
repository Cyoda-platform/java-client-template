package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class AutoDeliverProcessorTest {

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

        AutoDeliverProcessor processor = new AutoDeliverProcessor(serializerFactory);

        // Build a valid Shipment entity that passes isValid()
        Shipment.ShipmentItem item = new Shipment.ShipmentItem();
        item.setProductId("prod-1");
        item.setQty(1);
        List<Shipment.ShipmentItem> items = new ArrayList<>();
        items.add(item);

        Shipment shipment = new Shipment();
        shipment.setId("s1");
        shipment.setShipmentNumber("SN-1");
        shipment.setOrderId("o1");
        shipment.setStatus("SENT"); // Eligible for auto-deliver sunny path
        shipment.setCreatedAt("2020-01-01T00:00:00Z");
        shipment.setWarehouseId("w1");
        shipment.setItems(items);

        JsonNode entityJson = objectMapper.valueToTree(shipment);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(shipment.getId());
        request.setProcessorName("AutoDeliverProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
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

        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertEquals("DELIVERED", out.get("status").asText());
        assertNotNull(out.get("trackingInfo"));
        assertNotNull(out.get("trackingInfo").get("deliveredAt"));
        assertFalse(out.get("trackingInfo").get("deliveredAt").asText().isBlank());
    }
}
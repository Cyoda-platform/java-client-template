package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pickledger.version_1.PickLedger;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class AuditProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked per instructions (AuditProcessor requires it in constructor)
        EntityService entityService = mock(EntityService.class);

        AuditProcessor processor = new AuditProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid PickLedger entity (must pass isValid())
        PickLedger pick = new PickLedger();
        pick.setId(UUID.randomUUID().toString());
        pick.setOrderId(UUID.randomUUID().toString());
        pick.setProductId(UUID.randomUUID().toString());
        pick.setShipmentId(UUID.randomUUID().toString());
        pick.setQtyRequested(10);
        pick.setQtyPicked(5);
        // set timestamp to satisfy validation
        pick.setTimestamp(Instant.now().toString());
        // auditStatus left null/blank so processor may decide sampling

        // Convert entity to JsonNode for payload (serializer expects DataPayload.data as JsonNode)
        JsonNode entityJson = objectMapper.valueToTree(pick);
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(pick.getId());
        request.setProcessorName("AuditProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(ctx);

        // Assert basic successful processing
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Response should indicate success");

        // Inspect payload: at minimum entity id must be preserved and data present
        assertNotNull(response.getPayload(), "Response payload should not be null");
        assertNotNull(response.getPayload().getData(), "Response payload data should not be null");

        // Map output back to PickLedger to assert expected sunny-day properties
        PickLedger out;
        try {
            out = objectMapper.treeToValue(response.getPayload().getData(), PickLedger.class);
        } catch (Exception ex) {
            fail("Failed to deserialize response payload to PickLedger: " + ex.getMessage());
            return;
        }

        assertEquals(pick.getId(), out.getId(), "PickLedger id should be preserved after processing");

        // The processor may or may not select this entity for audit (random). If it did,
        // auditStatus should be AUDIT_PASSED or AUDIT_FAILED and auditorId/timestamp should be set.
        if (out.getAuditStatus() != null && !out.getAuditStatus().isBlank()) {
            assertTrue(
                    out.getAuditStatus().equalsIgnoreCase("AUDIT_PASSED")
                            || out.getAuditStatus().equalsIgnoreCase("AUDIT_FAILED"),
                    "If auditStatus set it must be AUDIT_PASSED or AUDIT_FAILED"
            );
            assertNotNull(out.getAuditorId(), "auditorId should be set when auditStatus updated");
            assertNotNull(out.getTimestamp(), "timestamp should be set when auditStatus updated");
        } else {
            // If not selected for audit, auditStatus may remain null/blank — that's still a valid sunny path
            assertTrue(out.getAuditStatus() == null || out.getAuditStatus().isBlank(),
                    "auditStatus may remain unset if not selected for audit");
        }
    }
}
package com.java_template.application.processor;
import com.java_template.application.entity.pickledger.version_1.PickLedger;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import java.util.ArrayList;

@Component
public class PickLedgerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PickLedgerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PickLedgerProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PickLedger for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PickLedger.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PickLedger entity) {
        return entity != null && entity.isValid();
    }

    private PickLedger processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PickLedger> context) {
        PickLedger entity = context.entity();
        try {
            logger.info("PickLedger id={} shipmentId={} processing audit", entity.getId(), entity.getShipmentId());

            // Only process audit if currently pending or not set
            String currentAudit = entity.getAuditStatus();
            if (currentAudit == null || currentAudit.isBlank() || "AUDIT_PENDING".equals(currentAudit)) {
                // Ensure timestamp exists
                if (entity.getTimestamp() == null || entity.getTimestamp().isBlank()) {
                    entity.setTimestamp(Instant.now().toString());
                }

                // 10% sampling for manual audit
                boolean sampled = ThreadLocalRandom.current().nextDouble() < 0.10d;
                if (sampled) {
                    // For sampled items, randomly decide pass/fail (80% pass chance)
                    boolean pass = ThreadLocalRandom.current().nextDouble() < 0.80d;
                    if (pass) {
                        entity.setAuditStatus("AUDIT_PASSED");
                    } else {
                        entity.setAuditStatus("AUDIT_FAILED");
                    }
                    entity.setAuditorId(UUID.randomUUID().toString());
                    logger.info("PickLedger id={} sampled for audit -> {}", entity.getId(), entity.getAuditStatus());
                } else {
                    // Not sampled -> mark as passed by default
                    entity.setAuditStatus("AUDIT_PASSED");
                    // no auditor assigned for non-sampled
                    entity.setAuditorId(null);
                    logger.info("PickLedger id={} not sampled -> AUDIT_PASSED", entity.getId());
                }
            } else {
                logger.info("PickLedger id={} already audited with status={}", entity.getId(), entity.getAuditStatus());
            }

            // After setting audit result, check whether the shipment can be marked as PICKED
            String shipmentId = entity.getShipmentId();
            if (shipmentId != null && !shipmentId.isBlank()) {
                try {
                    // Build condition to fetch all pick ledgers for this shipment
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                            Condition.of("$.shipmentId", "EQUALS", shipmentId)
                    );

                    CompletableFuture<List<DataPayload>> picksFuture = entityService.getItemsByCondition(
                            PickLedger.ENTITY_NAME,
                            PickLedger.ENTITY_VERSION,
                            condition,
                            true
                    );
                    List<DataPayload> pickPayloads = picksFuture.get();

                    boolean allPassed = true;
                    if (pickPayloads == null || pickPayloads.isEmpty()) {
                        allPassed = false;
                    } else {
                        for (DataPayload payload : pickPayloads) {
                            try {
                                PickLedger p = objectMapper.treeToValue(payload.getData(), PickLedger.class);
                                if (p == null) {
                                    allPassed = false;
                                    break;
                                }
                                String status = p.getAuditStatus();
                                if (status == null || !status.equals("AUDIT_PASSED")) {
                                    allPassed = false;
                                    break;
                                }
                            } catch (Exception e) {
                                logger.warn("Failed to parse PickLedger payload while evaluating shipment {}: {}", shipmentId, e.getMessage());
                                allPassed = false;
                                break;
                            }
                        }
                    }

                    if (allPassed) {
                        // Load shipment and update status to PICKED if not already
                        try {
                            CompletableFuture<DataPayload> shipFuture = entityService.getItem(UUID.fromString(shipmentId));
                            DataPayload shipPayload = shipFuture.get();
                            if (shipPayload != null) {
                                Shipment shipment = objectMapper.treeToValue(shipPayload.getData(), Shipment.class);
                                if (shipment != null) {
                                    String currentShipmentStatus = shipment.getStatus();
                                    if (currentShipmentStatus == null || !currentShipmentStatus.equals("PICKED")) {
                                        shipment.setStatus("PICKED");
                                        CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(shipment.getId()), shipment);
                                        updated.get(); // wait for completion
                                        logger.info("Shipment id={} marked as PICKED after all picks audited PASSED", shipment.getId());
                                    } else {
                                        logger.info("Shipment id={} already in status PICKED", shipment.getId());
                                    }
                                } else {
                                    logger.warn("Shipment payload parsed to null for id={}", shipmentId);
                                }
                            } else {
                                logger.warn("No shipment payload found for id={}", shipmentId);
                            }
                        } catch (Exception e) {
                            logger.error("Failed to update shipment {} status to PICKED: {}", shipmentId, e.getMessage(), e);
                        }
                    } else {
                        logger.info("Shipment id={} not all picks passed audit yet", shipmentId);
                    }

                } catch (Exception e) {
                    logger.error("Error while evaluating pick ledgers for shipment {}: {}", shipmentId, e.getMessage(), e);
                }
            } else {
                logger.warn("PickLedger id={} has no shipmentId, skipping shipment evaluation", entity.getId());
            }

        } catch (Exception ex) {
            logger.error("Unexpected error processing PickLedger id={}: {}", entity != null ? entity.getId() : "null", ex.getMessage(), ex);
        }

        return entity;
    }
}
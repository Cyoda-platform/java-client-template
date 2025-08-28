package com.java_template.application.processor;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.pickledger.version_1.PickLedger;
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

@Component
public class FulfillmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FulfillmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FulfillmentProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Shipment.class)
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

    private boolean isValidEntity(Shipment entity) {
        return entity != null && entity.isValid();
    }

    private Shipment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();
        try {
            logger.info("FulfillmentProcessor started for shipmentId={} orderId={}", shipment.getId(), shipment.getOrderId());

            // Transition to PICKING if currently PENDING_PICK
            if ("PENDING_PICK".equalsIgnoreCase(shipment.getStatus())) {
                shipment.setStatus("PICKING");
            }

            List<Shipment.ShipmentItem> items = shipment.getItems();
            if (items == null || items.isEmpty()) {
                logger.warn("Shipment {} has no items to pick", shipment.getId());
                return shipment;
            }

            boolean anyAuditFailed = false;

            for (Shipment.ShipmentItem item : items) {
                // Determine requested qty
                Integer qtyRequested = item.getQty() != null ? item.getQty() : 0;
                // Simulate pick: attempt to pick full requested quantity
                int qtyPicked = qtyRequested; // assume ideal pick; could be randomized if desired

                // Create PickLedger entry
                PickLedger pickLedger = new PickLedger();
                pickLedger.setId(UUID.randomUUID().toString());
                pickLedger.setShipmentId(shipment.getId());
                pickLedger.setOrderId(shipment.getOrderId());
                pickLedger.setProductId(item.getProductId());
                pickLedger.setQtyRequested(qtyRequested);
                pickLedger.setQtyPicked(qtyPicked);
                pickLedger.setTimestamp(Instant.now().toString());
                // Initially mark as AUDIT_PENDING
                pickLedger.setAuditStatus("AUDIT_PENDING");

                try {
                    CompletableFuture<java.util.UUID> fut = entityService.addItem(
                        PickLedger.ENTITY_NAME,
                        PickLedger.ENTITY_VERSION,
                        pickLedger
                    );
                    // wait for persistence to complete (synchronous in workflow)
                    fut.get();
                    logger.info("Created PickLedger {} for shipment={} product={} requested={} picked={}",
                            pickLedger.getId(), shipment.getId(), pickLedger.getProductId(), qtyRequested, qtyPicked);
                } catch (Exception ex) {
                    logger.error("Failed to persist PickLedger for shipment {} product {}: {}", shipment.getId(), item.getProductId(), ex.getMessage(), ex);
                }

                // Audit sampling: 10% chance that this pick is audited immediately
                double sample = Math.random();
                if (sample < 0.10) {
                    // perform audit outcome (random pass/fail)
                    boolean passed = Math.random() < 0.9; // bias towards pass
                    try {
                        // Read back or update audit by creating a new PickLedger object with same id but updated auditStatus
                        // We cannot update the previously created pickLedger via updateItem because we don't have its technical UUID,
                        // so create an updated object and call addItem for the audit result as a new record is acceptable for audit trace.
                        PickLedger auditRecord = new PickLedger();
                        auditRecord.setId(UUID.randomUUID().toString());
                        auditRecord.setShipmentId(shipment.getId());
                        auditRecord.setOrderId(shipment.getOrderId());
                        auditRecord.setProductId(item.getProductId());
                        auditRecord.setQtyRequested(qtyRequested);
                        auditRecord.setQtyPicked(qtyPicked);
                        auditRecord.setTimestamp(Instant.now().toString());
                        auditRecord.setAuditStatus(passed ? "AUDIT_PASSED" : "AUDIT_FAILED");
                        // auditorId can be left null (optional)
                        CompletableFuture<java.util.UUID> auditFut = entityService.addItem(
                            PickLedger.ENTITY_NAME,
                            PickLedger.ENTITY_VERSION,
                            auditRecord
                        );
                        auditFut.get();
                        logger.info("Audit sample for shipment={} product={} result={}", shipment.getId(), item.getProductId(), auditRecord.getAuditStatus());
                        if (!passed) {
                            anyAuditFailed = true;
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to persist audit PickLedger for shipment {} product {}: {}", shipment.getId(), item.getProductId(), ex.getMessage(), ex);
                    }
                }
            }

            // Decide shipment status after picks and audits
            if (anyAuditFailed) {
                // Keep in PICKING or mark as PICKED but require rework — here we remain in PICKING to indicate issues
                shipment.setStatus("PICKING");
                logger.info("Shipment {} has audit failures, remaining in PICKING", shipment.getId());
            } else {
                // All picks OK (or no sampled failures) -> advance to WAITING_TO_SEND
                shipment.setStatus("WAITING_TO_SEND");
                logger.info("Shipment {} advanced to WAITING_TO_SEND", shipment.getId());
            }

        } catch (Exception ex) {
            logger.error("Error processing fulfillment for shipment {}: {}", shipment != null ? shipment.getId() : "null", ex.getMessage(), ex);
        }

        return shipment;
    }
}
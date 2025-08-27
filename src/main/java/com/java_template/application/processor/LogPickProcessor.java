package com.java_template.application.processor;

import com.java_template.application.entity.pickledger.version_1.PickLedger;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

@Component
public class LogPickProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LogPickProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LogPickProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PickLedger for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PickLedger.class)
            // Use a relaxed validation for incoming pick ledger events:
            // PickLedger is an append-only audit entry so require only minimal fields (sku and delta).
            .validate(this::isValidEntity, "Invalid PickLedger state: missing required audit fields")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Relaxed validation: PickLedger entries are audit-only and may be created with missing
     * non-critical fields. Require only core audit data: sku and delta must be present.
     */
    private boolean isValidEntity(PickLedger entity) {
        if (entity == null) return false;
        if (entity.getSku() == null || entity.getSku().isBlank()) return false;
        if (entity.getDelta() == null) return false;
        // 'at', 'actor', 'pickId', 'note' are optional and will be set/normalized by processing logic.
        return true;
    }

    private PickLedger processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PickLedger> context) {
        PickLedger entity = context.entity();

        // Audit/logging: ensure basic audit fields are present and normalized.
        try {
            logger.info("Appending PickLedger entry - pickId: {}, orderId: {}, shipmentId: {}, sku: {}, delta: {}",
                    entity.getPickId(), entity.getOrderId(), entity.getShipmentId(), entity.getSku(), entity.getDelta());
        } catch (Exception e) {
            logger.warn("Failed to log PickLedger preview: {}", e.getMessage());
        }

        // Ensure pickId exists (generate if absent)
        if (entity.getPickId() == null || entity.getPickId().isBlank()) {
            entity.setPickId(UUID.randomUUID().toString());
            logger.debug("Generated pickId for PickLedger: {}", entity.getPickId());
        }

        // Ensure 'at' timestamp exists
        if (entity.getAt() == null || entity.getAt().isBlank()) {
            entity.setAt(Instant.now().toString());
            logger.debug("Set 'at' timestamp for PickLedger: {}", entity.getAt());
        }

        // Ensure actor is present (fallback to system)
        if (entity.getActor() == null || entity.getActor().isBlank()) {
            entity.setActor("system");
            logger.debug("Set default actor for PickLedger: system");
        }

        // Ensure note is not null to avoid null-related downstream issues
        if (entity.getNote() == null) {
            entity.setNote("");
        }

        // Delta should be present; if missing (defensive), set to 0
        if (entity.getDelta() == null) {
            entity.setDelta(0);
        }

        // Final audit log
        logger.info("PickLedger processed - pickId: {}, sku: {}, delta: {}, actor: {}, at: {}",
                entity.getPickId(), entity.getSku(), entity.getDelta(), entity.getActor(), entity.getAt());

        return entity;
    }
}
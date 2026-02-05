package com.example.application.processor.trade;

import com.example.application.entity.trade.version_1.Trade;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * RejectionHandlerProcessor - Handles trade rejection.
 * Persists rejection reason and notifies downstream audit/logging systems.
 * Manual transitions allowed for retry.
 * Execution Mode: SYNC
 */
@Component
public class RejectionHandlerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RejectionHandlerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RejectionHandlerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("RejectionHandlerProcessor: Handling rejection for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Trade.class)
                .validate(this::isValidEntity, "Invalid trade entity")
                .map(this::handleRejection)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Initial validation of entity wrapper
     */
    private boolean isValidEntity(EntityWithMetadata<Trade> entityWithMetadata) {
        Trade trade = entityWithMetadata.entity();
        return trade != null && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Handle trade rejection
     * TODO: Persist rejection reason and notify downstream systems
     */
    private EntityWithMetadata<Trade> handleRejection(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Trade> context) {

        EntityWithMetadata<Trade> entityWithMetadata = context.entityResponse();
        Trade trade = entityWithMetadata.entity();

        logger.debug("Handling rejection for trade ID: {}", trade.getId());

        try {
            // TODO: Persist rejection reason to database
            persistRejectionReason(trade);

            // TODO: Notify audit/logging systems
            notifyAuditSystem(trade);

            // TODO: Notify downstream systems of rejection
            notifyDownstreamSystems(trade);

            logger.info("Rejection handled successfully for trade ID: {}", trade.getId());
        } catch (Exception e) {
            logger.error("Failed to handle rejection for trade ID: {}", trade.getId(), e);
            throw new RuntimeException("Rejection handling failed: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Persist rejection reason to database
     * TODO: Implement actual persistence logic
     */
    private void persistRejectionReason(Trade trade) {
        logger.debug("Persisting rejection reason for trade ID: {}", trade.getId());
        // TODO: Save rejection details to database
        // - Store rejection timestamp
        // - Store rejection reason/error messages
        // - Store rejection details in audit table
    }

    /**
     * Notify audit/logging systems
     * TODO: Implement actual notification logic
     */
    private void notifyAuditSystem(Trade trade) {
        logger.debug("Notifying audit system for rejected trade ID: {}", trade.getId());
        // TODO: Call AuditService or AuditClient
        // TODO: Log rejection event with all relevant details
    }

    /**
     * Notify downstream systems of rejection
     * TODO: Implement actual notification logic
     */
    private void notifyDownstreamSystems(Trade trade) {
        logger.debug("Notifying downstream systems for rejected trade ID: {}", trade.getId());
        // TODO: Call notification service
        // TODO: Send rejection notification to relevant systems
        // TODO: Handle notification failures gracefully
    }
}


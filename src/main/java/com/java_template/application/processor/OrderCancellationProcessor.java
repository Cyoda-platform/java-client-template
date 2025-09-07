package com.java_template.application.processor;

import com.java_template.application.entity.petcareorder.version_1.PetCareOrder;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * OrderCancellationProcessor - Handles order cancellation business logic
 * 
 * Cancels an order before service completion, including:
 * - Validating cancellation eligibility
 * - Releasing reserved resources
 * - Processing refunds if applicable
 * - Recording cancellation details
 */
@Component
public class OrderCancellationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCancellationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OrderCancellationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order cancellation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(PetCareOrder.class)
            .validate(this::isValidEntityWithMetadata, "Invalid order cancellation data")
            .map(this::processEntityWithMetadataLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<PetCareOrder> entityWithMetadata) {
        PetCareOrder order = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();
        return order != null && order.isValid() && 
               ("PENDING".equals(currentState) || "CONFIRMED".equals(currentState));
    }

    private EntityWithMetadata<PetCareOrder> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<PetCareOrder> context) {
        
        EntityWithMetadata<PetCareOrder> entityWithMetadata = context.entityResponse();
        PetCareOrder order = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();

        logger.debug("Processing order cancellation for: {} in state: {}", order.getOrderId(), currentState);

        // 1. Validate cancellation eligibility
        validateCancellationEligibility(order, currentState);

        // 2. Calculate refund amount based on cancellation timing
        double refundAmount = calculateRefundAmount(order, currentState);

        // 3. Record cancellation details
        recordCancellationDetails(order, refundAmount);

        // 4. Resources are released (handled by state change)
        logger.info("Order {} cancelled successfully with refund amount: {}", order.getOrderId(), refundAmount);

        return entityWithMetadata;
    }

    private void validateCancellationEligibility(PetCareOrder order, String currentState) {
        // Check if order is not yet in progress
        if ("IN_PROGRESS".equals(currentState)) {
            throw new IllegalStateException("Cannot cancel order that is already in progress: " + order.getOrderId());
        }

        // Additional validation could be added here for specific business rules
        logger.debug("Cancellation eligibility validated for order {}", order.getOrderId());
    }

    private double calculateRefundAmount(PetCareOrder order, String currentState) {
        if (order.getCost() == null) {
            return 0.0;
        }

        double baseCost = order.getCost();
        
        // If order is still pending, full refund
        if ("PENDING".equals(currentState)) {
            return baseCost;
        }

        // If order is confirmed, calculate refund based on timing
        if ("CONFIRMED".equals(currentState)) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime scheduledDate = order.getScheduledDate();
            
            if (scheduledDate != null) {
                long hoursUntilService = ChronoUnit.HOURS.between(now, scheduledDate);
                
                // More than 24 hours: full refund
                if (hoursUntilService > 24) {
                    return baseCost;
                }
                // 12-24 hours: 75% refund
                else if (hoursUntilService > 12) {
                    return baseCost * 0.75;
                }
                // 2-12 hours: 50% refund
                else if (hoursUntilService > 2) {
                    return baseCost * 0.50;
                }
                // Less than 2 hours: 25% refund
                else {
                    return baseCost * 0.25;
                }
            }
        }

        // Default to no refund
        return 0.0;
    }

    private void recordCancellationDetails(PetCareOrder order, double refundAmount) {
        // Add cancellation information to notes
        String cancellationNote = String.format("Order cancelled at %s. Refund amount: $%.2f", 
                                               LocalDateTime.now(), refundAmount);
        
        String existingNotes = order.getNotes() != null ? order.getNotes() : "";
        String newNotes = existingNotes.isEmpty() ? cancellationNote : 
                         existingNotes + "; " + cancellationNote;
        
        order.setNotes(newNotes);

        logger.debug("Cancellation details recorded for order {}", order.getOrderId());
    }
}

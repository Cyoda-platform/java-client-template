package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.serializer.ErrorInfo;
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

import java.time.Instant;

@Component
public class PaymentAutoApproveProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentAutoApproveProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentAutoApproveProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Payment.class)
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

    private boolean isValidEntity(Payment entity) {
        return entity != null && entity.isValid();
    }

    private Payment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment entity = context.entity();

        // Only auto-approve payments that are in PENDING state.
        String currentStatus = entity.getStatus();
        if (currentStatus == null || !"PENDING".equalsIgnoreCase(currentStatus)) {
            logger.debug("Payment {} not in PENDING state (status={}). Auto-approve skipped.", entity.getId(), currentStatus);
            return entity;
        }

        // If already approvedAt present, skip to avoid double-approving
        if (entity.getApprovedAt() != null && !entity.getApprovedAt().isBlank()) {
            logger.debug("Payment {} already has approvedAt set ({}). Skipping auto-approve.", entity.getId(), entity.getApprovedAt());
            return entity;
        }

        // Business logic:
        // Wait 3 seconds, then mark payment as APPROVED and set approvedAt timestamp.
        try {
            logger.info("PaymentAutoApproveProcessor sleeping for 3s before auto-approving payment: {}", entity.getId());
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Auto-approve interrupted for payment {}: {}", entity.getId(), e.getMessage(), e);
            // If interrupted, set FAILED status to indicate auto-approval did not complete
            try {
                entity.setStatus("FAILED");
            } catch (Exception ex) {
                logger.warn("Unable to set payment status to FAILED for {}: {}", entity.getId(), ex.getMessage());
            }
            return entity;
        }

        // Apply approval
        try {
            entity.setStatus("APPROVED");
            entity.setApprovedAt(Instant.now().toString());
            logger.info("Payment {} auto-approved at {}", entity.getId(), entity.getApprovedAt());
        } catch (Exception e) {
            logger.error("Failed to update payment {} during auto-approve: {}", entity.getId(), e.getMessage(), e);
            // In case of any unexpected issue, mark as FAILED
            try {
                entity.setStatus("FAILED");
            } catch (Exception ex) {
                logger.warn("Unable to set payment status to FAILED for {}: {}", entity.getId(), ex.getMessage());
            }
        }

        return entity;
    }
}
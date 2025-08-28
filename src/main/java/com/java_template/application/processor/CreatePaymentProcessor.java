package com.java_template.application.processor;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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

@Component
public class CreatePaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreatePaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public CreatePaymentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AdoptionRequest.class)
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

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest entity = context.entity();

        // Business logic for CreatePaymentProcessor:
        // Triggered when an AdoptionRequest has been APPROVED and we need to create/initiate payment.
        // Rules implemented:
        // 1. If request.status == "APPROVED":
        //    - If adoptionFee == 0 -> skip payment, mark paymentStatus "PAID" and status "COMPLETED"
        //    - If adoptionFee > 0:
        //         - If paymentStatus is "NOT_PAID" -> set paymentStatus "PENDING" and status "PAYMENT_PENDING"
        //         - If paymentStatus already "PENDING" or "PAID" -> no-op
        // 2. If request.status is not "APPROVED", do nothing.
        // 3. If adoptionFee invalid (null or negative) log error and leave entity unchanged.

        String currentStatus = entity.getStatus();
        String currentPaymentStatus = entity.getPaymentStatus();
        Double fee = entity.getAdoptionFee();

        if (currentStatus == null) {
            logger.warn("AdoptionRequest {} has null status; skipping payment creation", entity.getRequestId());
            return entity;
        }

        if (!"APPROVED".equalsIgnoreCase(currentStatus)) {
            // Not in approved state, nothing to do for payment creation
            logger.debug("AdoptionRequest {} not in APPROVED state (status={}); skipping", entity.getRequestId(), currentStatus);
            return entity;
        }

        // Validate fee
        if (fee == null) {
            logger.error("AdoptionRequest {} has null adoptionFee; cannot create payment", entity.getRequestId());
            return entity;
        }
        if (fee < 0) {
            logger.error("AdoptionRequest {} has negative adoptionFee {}; cannot create payment", entity.getRequestId(), fee);
            return entity;
        }

        // Handle zero-fee: complete immediately
        if (fee == 0.0d) {
            logger.info("AdoptionRequest {} has zero adoptionFee; marking as PAID and COMPLETED", entity.getRequestId());
            entity.setPaymentStatus("PAID");
            entity.setStatus("COMPLETED");
            // Note: FinalizeAdoptionProcessor should handle pet/user updates on COMPLETED transition.
            return entity;
        }

        // For positive fee, initiate payment if not already initiated or paid
        if (currentPaymentStatus == null || currentPaymentStatus.isBlank() || "NOT_PAID".equalsIgnoreCase(currentPaymentStatus)) {
            logger.info("Creating payment for AdoptionRequest {}: fee={}", entity.getRequestId(), fee);
            entity.setPaymentStatus("PENDING");
            entity.setStatus("PAYMENT_PENDING");

            // Optionally, we would call an external payment gateway or create a Payment entity here.
            // No Payment entity exists in the current model, and per rules we should not update the triggering entity via EntityService.
            // The entity object is mutated and will be persisted by the workflow engine.
            return entity;
        }

        // If already pending or paid, no changes needed
        if ("PENDING".equalsIgnoreCase(currentPaymentStatus)) {
            logger.debug("AdoptionRequest {} payment already PENDING; no action", entity.getRequestId());
            return entity;
        }
        if ("PAID".equalsIgnoreCase(currentPaymentStatus)) {
            logger.debug("AdoptionRequest {} already PAID; updating status to COMPLETED if needed", entity.getRequestId());
            if (!"COMPLETED".equalsIgnoreCase(entity.getStatus())) {
                entity.setStatus("COMPLETED");
            }
            return entity;
        }

        // Fallback - no change
        logger.debug("AdoptionRequest {} in unhandled paymentStatus {}; no action taken", entity.getRequestId(), currentPaymentStatus);
        return entity;
    }
}
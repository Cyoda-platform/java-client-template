package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
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
public class ReactivateOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReactivateOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReactivateOwnerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Owner.class)
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

    private boolean isValidEntity(Owner entity) {
        return entity != null && entity.isValid();
    }

    private Owner processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Owner> context) {
        Owner entity = context.entity();

        // Business logic for reactivating an Owner:
        // - Reactivation is a manual operation that should transition the owner back to an active/verified state.
        // - Use available contact information to determine verification status.
        // - If contact email appears valid (simple contains '@'), mark as "verified".
        // - Otherwise, mark verificationStatus as "pending" so follow-up/verification is required.
        // - Ensure a default role exists (set to "user" if missing).
        // Do NOT perform add/update/delete operations on the triggering entity via EntityService here;
        // Cyoda will persist the mutated entity automatically.

        try {
            String email = entity.getContactEmail();
            String phone = entity.getContactPhone();
            String currentVerification = entity.getVerificationStatus();

            boolean hasValidEmail = email != null && !email.isBlank() && email.contains("@");
            boolean hasPhone = phone != null && !phone.isBlank();

            if (hasValidEmail) {
                entity.setVerificationStatus("verified");
                logger.info("Owner {} reactivated: verificationStatus set to 'verified' based on email", entity.getOwnerId());
            } else if (hasPhone) {
                // If no valid email but phone exists, mark pending verification but allow reactivation workflow to proceed.
                entity.setVerificationStatus("pending");
                logger.info("Owner {} reactivated: verificationStatus set to 'pending' based on phone presence", entity.getOwnerId());
            } else {
                // No contact info available to verify; set to pending and require manual verification.
                entity.setVerificationStatus("pending");
                logger.info("Owner {} reactivated: verificationStatus set to 'pending' (no contact info)", entity.getOwnerId());
            }

            // Ensure role is set to a sensible default if missing
            if (entity.getRole() == null || entity.getRole().isBlank()) {
                entity.setRole("user");
                logger.info("Owner {} role defaulted to 'user' during reactivation", entity.getOwnerId());
            }

            // Additional note: we do not modify savedPets/adoptedPets here.
            // If additional risk checks or external validations are required, they should be performed
            // by supplementary processors or manual review steps.

        } catch (Exception ex) {
            logger.error("Error processing reactivation for Owner {}: {}", entity != null ? entity.getOwnerId() : "unknown", ex.getMessage(), ex);
            // In case of unexpected error, attempt to mark verification status as pending to avoid leaving inconsistent state
            if (entity != null) {
                try {
                    entity.setVerificationStatus("pending");
                } catch (Exception ignore) {}
            }
        }

        return entity;
    }
}
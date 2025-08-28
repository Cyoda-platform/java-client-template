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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VerifyOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VerifyOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public VerifyOwnerProcessor(SerializerFactory serializerFactory) {
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

        // Business logic:
        // - If owner is already verified, no action required.
        // - If owner email domain is "example.com" we auto-verify (useful for internal/test users).
        // - Otherwise, simulate sending a verification email and ensure verified flag is set to false.
        if (entity == null) {
            logger.warn("Received null Owner entity in processing context");
            return entity;
        }

        Boolean verified = entity.getVerified();
        String email = entity.getEmail();

        if (verified != null && verified) {
            logger.info("Owner already verified: {}", email);
            return entity;
        }

        if (email == null || email.isBlank()) {
            logger.warn("Owner has no email, cannot send verification. Owner id: {}", entity.getId());
            // ensure verified is explicit false if missing
            if (entity.getVerified() == null) {
                entity.setVerified(false);
            }
            return entity;
        }

        String emailLower = email.toLowerCase().trim();
        if (emailLower.endsWith("@example.com")) {
            // Auto-verify internal/test domain users
            entity.setVerified(true);
            logger.info("Auto-verified owner with internal domain: {} (owner id: {})", email, entity.getId());
            return entity;
        }

        // Simulate sending verification email (actual sending would be implemented via external service)
        logger.info("Sending verification email to {} for owner id: {}", email, entity.getId());
        // Mark explicitly as not yet verified
        entity.setVerified(false);

        // Note: Do not call entityService.updateItem on the triggering entity.
        // Cyoda will persist changes made to the entity returned by this processor automatically.

        return entity;
    }
}
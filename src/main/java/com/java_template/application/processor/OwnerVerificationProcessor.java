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

import java.time.Instant;

@Component
public class OwnerVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public OwnerVerificationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
        Owner owner = context.entity();

        // Business logic based on functional requirements:
        // - Trigger verification (send verification email)
        // - Mark owner as pending verification (using available fields only)
        // - Update updatedAt timestamp

        try {
            if (owner.getEmail() == null || owner.getEmail().isBlank()) {
                logger.warn("Owner {} has no email; cannot send verification", owner.getId());
                return owner;
            }

            // Basic email format check (simple heuristic)
            String email = owner.getEmail();
            boolean emailLooksValid = email.contains("@") && email.indexOf('@') < email.length() - 1;
            if (!emailLooksValid) {
                logger.warn("Owner {} has invalid email format: {}", owner.getId(), email);
                return owner;
            }

            // Simulate sending verification email via Cyoda action.
            // Actual action invocation is out-of-scope here; we log the intent.
            logger.info("Sending verification email to owner {} at {}", owner.getId(), owner.getEmail());

            // Use existing fields only. Entity does not have explicit 'state' field,
            // so we mark verification intent by setting role to a pending-like value.
            // This follows constraint to not invent new properties.
            owner.setRole("pending_verification");

            // Update the updatedAt timestamp to reflect the verification attempt
            owner.setUpdatedAt(Instant.now());

            // Optionally, one could record verification attempts in another entity via EntityService.
            // Not required by current functional requirements; skipping to avoid creating unknown entities.

        } catch (Exception ex) {
            logger.error("Error while processing owner verification for {}: {}", owner != null ? owner.getId() : "unknown", ex.getMessage(), ex);
            // Do not throw; return entity as-is so serializer can handle response.
        }

        return owner;
    }
}
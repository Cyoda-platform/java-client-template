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
public class ProfileCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProfileCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProfileCompletionProcessor(SerializerFactory serializerFactory) {
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
        Owner owner = context.entity();
        if (owner == null) {
            logger.warn("Owner entity is null in execution context");
            return null;
        }

        logger.info("Evaluating profile completeness for owner id={}", owner.getId());

        // Normalize basic fields
        if (owner.getName() != null) {
            owner.setName(owner.getName().trim());
        }

        if (owner.getAddress() != null) {
            owner.setAddress(owner.getAddress().trim());
        }

        if (owner.getPreferences() != null) {
            owner.setPreferences(owner.getPreferences().trim());
        }

        if (owner.getContactEmail() != null) {
            owner.setContactEmail(owner.getContactEmail().trim().toLowerCase());
        }

        if (owner.getContactPhone() != null) {
            owner.setContactPhone(normalizePhone(owner.getContactPhone()));
        }

        boolean hasAddress = owner.getAddress() != null && !owner.getAddress().isBlank();
        boolean hasPreferences = owner.getPreferences() != null && !owner.getPreferences().isBlank();

        boolean hasValidEmail = false;
        if (owner.getContactEmail() != null && !owner.getContactEmail().isBlank()) {
            hasValidEmail = isValidEmail(owner.getContactEmail());
            if (!hasValidEmail) {
                logger.warn("Owner id={} has invalid email format: {}", owner.getId(), owner.getContactEmail());
            }
        }

        boolean hasPhone = owner.getContactPhone() != null && !owner.getContactPhone().isBlank();

        boolean profileComplete = hasAddress && (hasValidEmail || hasPhone) && hasPreferences;

        if (profileComplete) {
            logger.info("Owner profile marked complete for id={}", owner.getId());
            // Business rule: profile is complete; ensure fields are normalized (done above).
            // There is no explicit 'status' or 'profileComplete' flag on Owner entity, so we only normalize and log.
        } else {
            StringBuilder missing = new StringBuilder();
            if (!hasAddress) missing.append("address ");
            if (!(hasValidEmail || hasPhone)) missing.append("contact ");
            if (!hasPreferences) missing.append("preferences ");
            logger.info("Owner profile incomplete for id={}. Missing: {}", owner.getId(), missing.toString().trim());
        }

        return owner;
    }

    // Simple email validation (basic)
    private boolean isValidEmail(String email) {
        if (email == null) return false;
        String trimmed = email.trim();
        // Very simple regex to avoid external libs; covers common cases.
        return trimmed.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    // Normalize phone by removing common separators; preserve leading '+' if present
    private String normalizePhone(String phone) {
        if (phone == null) return null;
        String p = phone.trim();
        boolean leadingPlus = p.startsWith("+");
        // Remove all non-digit characters
        String digits = p.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return "";
        return leadingPlus ? "+" + digits : digits;
    }
}
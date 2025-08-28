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
public class OwnerValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public OwnerValidationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        Owner entity = context.entity();

        // Normalize email/phone if present
        String email = entity.getContactEmail();
        String phone = entity.getContactPhone();
        if (email != null) {
            email = email.strip();
            entity.setContactEmail(email);
        }
        if (phone != null) {
            phone = phone.strip();
            entity.setContactPhone(phone);
        }

        // If owner is staff or admin, auto-verify
        String role = entity.getRole();
        if (role != null) {
            String roleLower = role.strip().toLowerCase();
            if ("staff".equals(roleLower) || "admin".equals(roleLower)) {
                entity.setVerificationStatus("verified");
                logger.info("Owner {} auto-verified due to role {}", entity.getOwnerId(), role);
                return entity;
            }
        }

        // Simple email and phone format checks
        boolean emailValid = false;
        boolean phoneValid = false;

        if (email != null && !email.isBlank()) {
            // basic email regex
            emailValid = email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
        }

        if (phone != null && !phone.isBlank()) {
            // basic phone regex: allows digits, spaces, plus, hyphen, parentheses
            phoneValid = phone.matches("^[+]?[-() 0-9]{7,20}$");
        }

        // Determine verification status:
        // - verified if email OR phone is valid
        // - pending if neither valid
        // Preserve "verified" if already verified and still valid
        String currentStatus = entity.getVerificationStatus();
        if ("verified".equalsIgnoreCase(currentStatus) && (emailValid || phoneValid)) {
            // Keep verified
            entity.setVerificationStatus("verified");
            logger.debug("Owner {} remains verified", entity.getOwnerId());
        } else if (emailValid || phoneValid) {
            entity.setVerificationStatus("verified");
            logger.info("Owner {} set to verified (emailValid={}, phoneValid={})", entity.getOwnerId(), emailValid, phoneValid);
        } else {
            entity.setVerificationStatus("pending");
            logger.info("Owner {} set to pending verification (invalid contact info)", entity.getOwnerId());
        }

        return entity;
    }
}
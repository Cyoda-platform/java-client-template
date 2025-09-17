package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
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

import java.util.regex.Pattern;

/**
 * RegisterOwnerProcessor - Validates and completes owner registration
 * 
 * Purpose: Complete owner registration with validation
 * Transition: initial_state -> registered (automatic)
 */
@Component
public class RegisterOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RegisterOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );

    // Phone validation pattern (basic format)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^[\\+]?[1-9]?[0-9]{7,15}$"
    );

    public RegisterOwnerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner registration for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Owner.class)
                .validate(this::isValidEntityWithMetadata, "Invalid owner entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Owner> entityWithMetadata) {
        Owner entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic processing method
     * Validates email format, phone format, and address completeness
     */
    private EntityWithMetadata<Owner> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Owner> context) {

        EntityWithMetadata<Owner> entityWithMetadata = context.entityResponse();
        Owner entity = entityWithMetadata.entity();

        logger.debug("Registering owner: {}", entity.getOwnerId());

        // Validate owner.email format
        if (!isValidEmail(entity.getEmail())) {
            logger.warn("Owner {} has invalid email format: {}", 
                       entity.getOwnerId(), entity.getEmail());
        }

        // Validate owner.phone format
        if (!isValidPhone(entity.getPhone())) {
            logger.warn("Owner {} has invalid phone format: {}", 
                       entity.getOwnerId(), entity.getPhone());
        }

        // Validate owner.address is complete
        if (entity.getAddress() == null || !entity.getAddress().isValid()) {
            logger.warn("Owner {} has incomplete address information", entity.getOwnerId());
        }

        logger.info("Owner {} registered successfully", entity.getOwnerId());

        return entityWithMetadata;
    }

    /**
     * Validate email format
     */
    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validate phone format
     */
    private boolean isValidPhone(String phone) {
        if (phone == null) {
            return false;
        }
        // Remove common phone formatting characters
        String cleanPhone = phone.replaceAll("[\\s\\-\\(\\)]", "");
        return PHONE_PATTERN.matcher(cleanPhone).matches();
    }
}

package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
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
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class ActivateOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ActivateOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // simple email validation pattern (sufficient for business-rule level validation)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public ActivateOwnerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Owner.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
            logger.warn("ActivateOwnerProcessor: received null owner in execution context");
            return null;
        }

        String currentStatus = owner.getAccountStatus();
        logger.debug("ActivateOwnerProcessor: current accountStatus={}, ownerId={}", currentStatus, owner.getId());

        // Idempotent: if already active, nothing to do
        if ("active".equalsIgnoreCase(currentStatus)) {
            logger.info("Owner {} is already active. No action taken.", owner.getId());
            return owner;
        }

        // Only activate owners that are pending verification (business rule)
        if (!"pending_verification".equalsIgnoreCase(currentStatus)) {
            logger.info("Owner {} is in '{}' state; ActivateOwnerProcessor will not change it.", owner.getId(), currentStatus);
            return owner;
        }

        // Validate contact email presence and format
        boolean emailValid = false;
        try {
            if (owner.getContact() != null && owner.getContact().getEmail() != null) {
                String email = owner.getContact().getEmail().trim();
                emailValid = EMAIL_PATTERN.matcher(email).matches();
            }
        } catch (Exception e) {
            logger.warn("ActivateOwnerProcessor: error validating email for owner {}: {}", owner.getId(), e.getMessage());
            emailValid = false;
        }

        if (!emailValid) {
            // Do not activate if contact email is missing/invalid. Leave as pending_verification.
            logger.info("ActivateOwnerProcessor: owner {} has invalid or missing contact email; remaining in pending_verification.", owner.getId());
            return owner;
        }

        // All checks passed: activate owner
        owner.setAccountStatus("active");
        try {
            // update audit timestamp if available
            if (Objects.nonNull(owner.getUpdatedAt())) {
                owner.setUpdatedAt(Instant.now().toString());
            } else {
                // If updatedAt accessor exists but returns null, still attempt to set it
                owner.setUpdatedAt(Instant.now().toString());
            }
        } catch (Exception e) {
            // If entity doesn't have updatedAt setter/getter behavior expected, ignore and continue.
            logger.debug("ActivateOwnerProcessor: unable to set updatedAt for owner {}: {}", owner.getId(), e.getMessage());
        }

        logger.info("ActivateOwnerProcessor: owner {} activated successfully.", owner.getId());
        return owner;
    }
}
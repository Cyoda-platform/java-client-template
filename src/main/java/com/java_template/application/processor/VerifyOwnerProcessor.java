package com.java_template.application.processor;
import com.java_template.application.entity.owner.version_1.Owner;
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

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;
import java.lang.reflect.Method;

@Component
public class VerifyOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VerifyOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final Pattern SIMPLE_EMAIL_PATTERN = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public VerifyOwnerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Owner.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Owner entity) {
        return entity != null;
    }

    private Owner processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Owner> context) {
        Owner owner = context.entity();
        if (owner == null) {
            logger.warn("Owner entity is null in execution context");
            return owner;
        }

        try {
            // Read current accountStatus via reflection if available
            String currentStatus = null;
            try {
                Method getAccountStatus = owner.getClass().getMethod("getAccountStatus");
                Object statusObj = getAccountStatus.invoke(owner);
                if (statusObj != null) currentStatus = statusObj.toString();
            } catch (NoSuchMethodException nsme) {
                // fallback to try isAccountStatus or accountStatus field access not attempted here
                logger.debug("getAccountStatus method not found on Owner, assuming status unknown");
            }

            // Only attempt verification when owner is pending verification (idempotent)
            if (currentStatus != null && !"pending_verification".equalsIgnoreCase(currentStatus)) {
                logger.info("Owner {} is not in pending_verification state (current={}), skipping verification", safeGetId(owner), currentStatus);
                return owner;
            }

            // Extract contact email via reflection if present
            String email = null;
            try {
                Method getContact = owner.getClass().getMethod("getContact");
                Object contact = getContact.invoke(owner);
                if (contact != null) {
                    try {
                        Method getEmail = contact.getClass().getMethod("getEmail");
                        Object emailObj = getEmail.invoke(contact);
                        if (emailObj != null) email = emailObj.toString();
                    } catch (NoSuchMethodException e) {
                        logger.debug("contact.getEmail() not found, falling back to contact.toString()");
                        email = contact.toString();
                    }
                }
            } catch (NoSuchMethodException nsme) {
                logger.debug("getContact method not found on Owner");
            }

            boolean emailValid = email != null && SIMPLE_EMAIL_PATTERN.matcher(email).matches();

            if (emailValid) {
                // set accountStatus = "active" via reflection setter if present
                try {
                    Method setAccountStatus = owner.getClass().getMethod("setAccountStatus", String.class);
                    setAccountStatus.invoke(owner, "active");
                    logger.info("Owner {} email verified ({}). Account activated.", safeGetId(owner), email);
                } catch (NoSuchMethodException e) {
                    logger.warn("setAccountStatus(String) not found on Owner. Unable to update accountStatus for owner {}", safeGetId(owner));
                }

                // update updatedAt timestamp if setter exists
                try {
                    Method setUpdatedAt = owner.getClass().getMethod("setUpdatedAt", String.class);
                    setUpdatedAt.invoke(owner, Instant.now().toString());
                } catch (NoSuchMethodException e) {
                    // ignore if not present
                }

            } else {
                logger.warn("Owner {} verification failed: invalid or missing email ({}). Remaining in pending_verification.", safeGetId(owner), email);
                // leave accountStatus as-is for manual review; update updatedAt to reflect attempted verification if possible
                try {
                    Method setUpdatedAt = owner.getClass().getMethod("setUpdatedAt", String.class);
                    setUpdatedAt.invoke(owner, Instant.now().toString());
                } catch (NoSuchMethodException e) {
                    // ignore
                }
            }

        } catch (Exception ex) {
            logger.error("Error while verifying owner {}: {}", safeGetId(owner), ex.getMessage(), ex);
            // Do not throw; leave entity state as-is to allow retries
        }

        return owner;
    }

    private String safeGetId(Owner owner) {
        if (owner == null) return "null-owner";
        try {
            Method getId = owner.getClass().getMethod("getId");
            Object idObj = getId.invoke(owner);
            return Objects.toString(idObj, "unknown-id");
        } catch (Exception e) {
            return "unknown-id";
        }
    }
}
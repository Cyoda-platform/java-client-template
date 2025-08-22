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

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.regex.Pattern;

@Component
public class ActivateOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ActivateOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // simple RFC-5322-ish email regex for basic validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
        Pattern.CASE_INSENSITIVE
    );

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
        Owner entity = context.entity();

        try {
            String currentStatus = null;
            try {
                currentStatus = entity.getAccountStatus();
            } catch (Exception e) {
                logger.warn("Unable to read accountStatus from Owner entity (technicalId={}): {}", context.request().getEntityId(), e.getMessage());
            }

            if (currentStatus == null) {
                logger.debug("Owner.accountStatus is null, no action taken (technicalId={})", context.request().getEntityId());
                return entity;
            }

            // Only activate owners that are pending verification
            if ("pending_verification".equalsIgnoreCase(currentStatus.trim())) {
                String email = extractContactEmail(entity);
                if (email != null && isValidEmail(email)) {
                    logger.info("Activating owner (technicalId={}) - valid contact email found.", context.request().getEntityId());
                    entity.setAccountStatus("active");
                    try {
                        entity.setUpdatedAt(Instant.now().toString());
                    } catch (Exception e) {
                        // If setUpdatedAt not present, ignore - not critical for activation logic
                        logger.debug("setUpdatedAt not available on Owner entity: {}", e.getMessage());
                    }
                } else {
                    logger.info("Owner (technicalId={}) remains pending_verification - contact email invalid or missing.", context.request().getEntityId());
                    // Do not change accountStatus; leave for manual verification or other processors
                }
            } else {
                logger.debug("Owner (technicalId={}) accountStatus is '{}', no activation needed.", context.request().getEntityId(), currentStatus);
            }
        } catch (Exception ex) {
            logger.error("Unexpected error processing ActivateOwnerProcessor for entityId={}: {}", context.request().getEntityId(), ex.getMessage(), ex);
            // Do not throw; processors should be tolerant. The entity will be persisted as-is.
        }

        return entity;
    }

    /**
     * Attempts to extract contact.email from Owner entity without assuming concrete Contact type.
     * Uses reflection to be resilient to variations in the Contact representation.
     */
    private String extractContactEmail(Owner owner) {
        try {
            Object contact = null;
            try {
                Method getContact = owner.getClass().getMethod("getContact");
                contact = getContact.invoke(owner);
            } catch (NoSuchMethodException nsme) {
                // No getContact method - cannot extract email
                logger.debug("Owner.getContact() not available: {}", nsme.getMessage());
                return null;
            }

            if (contact == null) {
                return null;
            }

            try {
                Method getEmail = contact.getClass().getMethod("getEmail");
                Object emailObj = getEmail.invoke(contact);
                return emailObj != null ? String.valueOf(emailObj) : null;
            } catch (NoSuchMethodException nsme) {
                // contact might be a Map-like structure with get(String) - try toString fallback
                logger.debug("Contact.getEmail() not available, attempting toString fallback.");
                return contact.toString();
            }
        } catch (Exception e) {
            logger.debug("Failed to extract contact email via reflection: {}", e.getMessage());
            return null;
        }
    }

    private boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email).matches();
    }
}
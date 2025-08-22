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
import java.util.regex.Pattern;

@Component
public class VerifyOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VerifyOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public VerifyOwnerProcessor(SerializerFactory serializerFactory) {
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
        return entity != null && entity.getId() != null;
    }

    private Owner processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Owner> context) {
        Owner owner = context.entity();
        try {
            String currentStatus = owner.getAccountStatus();
            // If already active, nothing to do (idempotent)
            if ("active".equalsIgnoreCase(currentStatus)) {
                logger.info("Owner {} already active, no action taken.", owner.getId());
                return owner;
            }

            // Basic contact checks
            Object contact = owner.getContact();
            if (contact == null) {
                logger.warn("Owner {} has no contact information; leaving status as '{}'", owner.getId(), currentStatus);
                // keep status as-is (likely pending_verification)
                return owner;
            }

            // Attempt to read email via getter if available
            String email = null;
            try {
                // contact is a typed POJO; call its getEmail() via typical accessor
                // We rely on the entity model providing getContact().getEmail()
                email = (String) contact.getClass().getMethod("getEmail").invoke(contact);
            } catch (NoSuchMethodException nsme) {
                logger.warn("Contact object for owner {} does not expose getEmail(); leaving status as '{}'", owner.getId(), currentStatus);
            } catch (Exception e) {
                logger.warn("Error while accessing email for owner {}: {}; leaving status as '{}'", owner.getId(), e.getMessage(), currentStatus);
            }

            if (email == null || email.trim().isEmpty()) {
                logger.info("Owner {} email missing or empty; verification cannot proceed.", owner.getId());
                // leave as pending_verification
                return owner;
            }

            // Validate email format
            if (EMAIL_PATTERN.matcher(email.trim()).matches()) {
                owner.setAccountStatus("active");
                // update timestamp to reflect change
                try {
                    owner.setUpdatedAt(Instant.now().toString());
                } catch (Exception ignored) {
                    // If setUpdatedAt not available, ignore - persistence will handle timestamps if needed
                }
                logger.info("Owner {} verified successfully via email '{}'. Status set to 'active'.", owner.getId(), email);
            } else {
                logger.info("Owner {} provided invalid email '{}'; leaving status as '{}'", owner.getId(), email, currentStatus);
                // keep as pending_verification or current status
            }

        } catch (Exception ex) {
            logger.error("Unexpected error while verifying owner {}: {}", owner != null ? owner.getId() : "unknown", ex.getMessage(), ex);
            // Do not throw; leave entity state unchanged for manual review / retry
        }

        return owner;
    }
}
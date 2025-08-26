package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.net.URI;

@Component
public class ValidateSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateSubscriberProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        if (entity == null) return false;
        // Ensure required identifying/processing fields exist before running validation logic.
        if (entity.getId() == null || entity.getId().isBlank()) return false;
        if (entity.getContact() == null || entity.getContact().isBlank()) return false;
        if (entity.getType() == null || entity.getType().isBlank()) return false;
        if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) return false;
        // active may be null initially; processor will determine and set it.
        return true;
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();

        try {
            // Normalize contact and type values
            String contact = entity.getContact() != null ? entity.getContact().trim() : null;
            String type = entity.getType() != null ? entity.getType().trim().toLowerCase() : null;
            entity.setContact(contact);
            entity.setType(type);

            boolean valid = false;

            if ("email".equals(type)) {
                // Basic email validation regex
                // Note: simple regex sufficient for most email formats
                String emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
                if (contact != null && contact.matches(emailRegex)) {
                    valid = true;
                } else {
                    logger.warn("Subscriber {} failed email validation for contact={}", entity.getId(), contact);
                }
            } else if ("webhook".equals(type)) {
                // Validate webhook URL (must be http or https)
                if (contact != null) {
                    try {
                        URI uri = new URI(contact);
                        String scheme = uri.getScheme();
                        String host = uri.getHost();
                        if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) && host != null && !host.isBlank()) {
                            valid = true;
                        } else {
                            logger.warn("Subscriber {} webhook URL missing valid scheme/host: {}", entity.getId(), contact);
                        }
                    } catch (Exception e) {
                        logger.warn("Subscriber {} invalid webhook URL: {} - {}", entity.getId(), contact, e.getMessage());
                    }
                } else {
                    logger.warn("Subscriber {} webhook contact is null or blank", entity.getId());
                }
            } else {
                // Unknown types: treat as not validated automatically; require manual review
                logger.warn("Subscriber {} has unsupported type '{}' - marking as inactive for manual review", entity.getId(), type);
            }

            // Set active flag based on validation result
            entity.setActive(valid);

            // Optionally, if invalid, ensure lastNotifiedAt remains unchanged (do not set here).
            if (valid) {
                logger.info("Subscriber {} validated successfully and marked active", entity.getId());
            } else {
                logger.info("Subscriber {} validation failed and marked inactive", entity.getId());
            }

        } catch (Exception ex) {
            logger.error("Error validating subscriber {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
            // On unexpected error, mark as inactive for manual review
            if (entity != null) {
                entity.setActive(false);
            }
        }

        return entity;
    }
}
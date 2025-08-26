package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

@Component
public class SendEmailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendEmailProcessor(SerializerFactory serializerFactory) {
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
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();

        // Business rules:
        // - Only attempt delivery for active subscribers who haven't opted out and have a valid email.
        // - If delivery criteria are met and email looks valid (basic check for '@'), mark SUCCESS.
        // - Otherwise mark FAILED.
        // - We must not call entityService.updateItem on this Subscriber (it will be persisted by Cyoda automatically).
        try {
            boolean active = Boolean.TRUE.equals(entity.getActive());
            String optOutAt = entity.getOptOutAt();
            String email = entity.getEmail();

            if (!active) {
                logger.info("Subscriber {} is not active. Marking delivery as FAILED.", entity.getId());
                entity.setLastDeliveryStatus("FAILED");
                return entity;
            }

            if (optOutAt != null && !optOutAt.isBlank()) {
                logger.info("Subscriber {} has opted out at {}. Marking delivery as FAILED.", entity.getId(), optOutAt);
                entity.setLastDeliveryStatus("FAILED");
                return entity;
            }

            if (email == null || email.isBlank()) {
                logger.info("Subscriber {} has no email. Marking delivery as FAILED.", entity.getId());
                entity.setLastDeliveryStatus("FAILED");
                return entity;
            }

            // Basic email validity check: presence of '@' and a domain part
            String trimmed = email.trim();
            boolean looksValid = trimmed.contains("@") && trimmed.indexOf('@') > 0 && trimmed.indexOf('@') < trimmed.length() - 1;

            if (!looksValid) {
                logger.info("Subscriber {} has invalid email '{}'. Marking delivery as FAILED.", entity.getId(), email);
                entity.setLastDeliveryStatus("FAILED");
                return entity;
            }

            // Simulate sending email. In a real implementation this would call an external mail service.
            // For the processor, reflect success status based on the checks above.
            logger.info("Simulating email send to {} for subscriber {}", email, entity.getId());
            entity.setLastDeliveryStatus("SUCCESS");

            return entity;
        } catch (Exception ex) {
            logger.error("Error while processing SendEmail for subscriber {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
            if (entity != null) {
                entity.setLastDeliveryStatus("FAILED");
            }
            return entity;
        }
    }
}
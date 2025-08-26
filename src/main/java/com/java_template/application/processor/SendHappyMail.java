package com.java_template.application.processor;
import com.java_template.application.entity.mail.version_1.Mail;
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

import java.util.List;

@Component
public class SendHappyMail implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendHappyMail.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendHappyMail(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Mail.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Mail entity) {
        return entity != null && entity.isValid();
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail entity = context.entity();
        List<String> recipients = entity.getMailList();

        // Business logic: send happy template to each recipient.
        // If any delivery fails, throw an exception so workflow marks this processing as FAILED.
        // We cannot modify fields that don't exist on the Mail entity (e.g., status/notes), so success is implied by completing without exception.
        if (recipients == null || recipients.isEmpty()) {
            logger.warn("No recipients to send happy mail for entity id={}", entity.getId());
            return entity;
        }

        for (String recipient : recipients) {
            try {
                // Simple validation: treat emails containing '@' as deliverable.
                if (recipient == null || recipient.isBlank() || !recipient.contains("@")) {
                    String msg = String.format("Invalid recipient address '%s' for entity id=%s", recipient, entity.getId());
                    logger.error(msg);
                    throw new RuntimeException(msg);
                }

                // Simulate sending happy mail. Replace with actual sending integration if available.
                logger.info("Sent happy mail to {} for entity id={}", recipient, entity.getId());
            } catch (RuntimeException ex) {
                // Log and rethrow to indicate processing failure (will transition workflow to FAILED)
                logger.error("Failed to send happy mail to {} for entity id={}: {}", recipient, entity.getId(), ex.getMessage());
                throw ex;
            } catch (Exception ex) {
                logger.error("Unexpected error while sending happy mail to {} for entity id={}", recipient, entity.getId(), ex);
                throw new RuntimeException("Unexpected error sending mail", ex);
            }
        }

        // All deliveries simulated as successful
        logger.info("All happy mails processed successfully for entity id={}", entity.getId());
        return entity;
    }
}
package com.java_template.application.processor;

import com.java_template.application.entity.mail.version_1.Mail;
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class SendGloomyMail implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendGloomyMail.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // simple email pattern (not exhaustive but adequate for basic validation)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public SendGloomyMail(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Mail.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        // Business logic for sending gloomy mail:
        // - Iterate recipients in mailList
        // - Validate recipient email format
        // - "Send" gloomy template to valid recipients (simulated)
        // - Remove invalid recipients from mailList (persisted by Cyoda automatically)
        // - Log successes and failures
        // Note: Mail entity has limited fields (id, isHappy, mailList). We must use existing getters/setters only.

        List<String> originalList = entity.getMailList();
        if (originalList == null || originalList.isEmpty()) {
            logger.warn("Mail (id={}) has empty mailList. Nothing to send.", entity.getId());
            return entity;
        }

        List<String> delivered = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (String recipient : originalList) {
            if (recipient == null || recipient.isBlank()) {
                failed.add(recipient);
                logger.warn("Skipping blank recipient for mail id={}", entity.getId());
                continue;
            }
            boolean validEmail = EMAIL_PATTERN.matcher(recipient).matches();
            if (!validEmail) {
                failed.add(recipient);
                logger.warn("Invalid email format for recipient '{}' in mail id={}", recipient, entity.getId());
                continue;
            }

            // Simulate sending gloomy template.
            // In a real implementation, this is where you'd call an email service.
            try {
                // Simulated send: assume success for valid format
                logger.info("Sending gloomy template to '{}' for mail id={}", recipient, entity.getId());
                delivered.add(recipient);
            } catch (Exception ex) {
                failed.add(recipient);
                logger.error("Failed to send to '{}' for mail id={}. Reason: {}", recipient, entity.getId(), ex.getMessage());
            }
        }

        // Update mailList to contain only delivered recipients to reflect what was actually sent.
        // This mutates the entity; Cyoda will persist the entity state automatically.
        entity.setMailList(new ArrayList<>(delivered));

        if (failed.isEmpty()) {
            logger.info("All gloomy mails delivered for mail id={}. Delivered count={}", entity.getId(), delivered.size());
        } else {
            logger.error("Gloomy mail delivery had failures for mail id={}. Delivered={}, Failed={}", entity.getId(), delivered, failed);
        }

        return entity;
    }
}
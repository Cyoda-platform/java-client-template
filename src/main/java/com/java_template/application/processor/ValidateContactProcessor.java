package com.java_template.application.processor;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Component
public class ValidateContactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateContactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final Pattern EMAIL_REGEX = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public ValidateContactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
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

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();

        try {
            String email = entity.getEmail();
            String webhook = entity.getWebhookUrl();

            boolean validEmail = false;
            boolean validWebhook = false;

            if (email != null && !email.isBlank()) {
                Matcher m = EMAIL_REGEX.matcher(email.trim());
                validEmail = m.matches();
            }

            if (webhook != null && !webhook.isBlank()) {
                String w = webhook.trim().toLowerCase();
                validWebhook = w.startsWith("http://") || w.startsWith("https://");
            }

            boolean shouldBeActive = validEmail || validWebhook;
            // Set active flag according to validation result
            entity.setActive(shouldBeActive);

            logger.info("Subscriber [{}] contact validation result: emailValid={}, webhookValid={}, set active={}",
                    entity.getId(), validEmail, validWebhook, shouldBeActive);

        } catch (Exception ex) {
            // In case of unexpected errors mark as inactive and record in logs
            logger.error("Error while validating subscriber contact: {}", ex.getMessage(), ex);
            try {
                entity.setActive(false);
            } catch (Exception e) {
                logger.error("Failed to set subscriber active=false: {}", e.getMessage(), e);
            }
        }

        return entity;
    }
}
package com.java_template.application.processor;

import com.java_template.application.entity.emailnotification.version_1.EmailNotification;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class EmailRetryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailRetryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public EmailRetryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailNotification retry for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailNotification.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(EmailNotification entity) {
        return entity != null && entity.isValid();
    }

    private EmailNotification processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<EmailNotification> context) {
        EmailNotification entity = context.entity();

        logger.info("Retrying email send for: {}", entity.getRecipientEmail());

        // Increment retry count
        int currentRetryCount = entity.getRetryCount() != null ? entity.getRetryCount() : 0;
        entity.setRetryCount(currentRetryCount + 1);

        // Check if within retry limit
        int maxRetries = entity.getMaxRetries() != null ? entity.getMaxRetries() : 3;
        
        if (entity.getRetryCount() > maxRetries) {
            // Retry limit exceeded
            entity.setDeliveryStatus("FAILED");
            entity.setErrorMessage("Maximum retry attempts exceeded (" + maxRetries + ")");
            logger.error("Email retry limit exceeded for: {}", entity.getRecipientEmail());
            return entity;
        }

        // Wait for exponential backoff delay (simulated)
        int delayMinutes = calculateBackoffDelay(entity.getRetryCount());
        logger.info("Retry attempt {} for {} (delay: {} minutes)", 
                   entity.getRetryCount(), entity.getRecipientEmail(), delayMinutes);

        // Clear previous error message for retry
        entity.setErrorMessage(null);

        // Reset delivery status for retry
        entity.setDeliveryStatus("PENDING");

        // Update scheduled send time for retry
        entity.setScheduledSendTime(LocalDateTime.now().plusMinutes(delayMinutes));

        logger.info("Email queued for retry {} for: {}", entity.getRetryCount(), entity.getRecipientEmail());
        return entity;
    }

    private int calculateBackoffDelay(int retryCount) {
        // Exponential backoff: 2^retryCount minutes
        // Retry 1: 2 minutes
        // Retry 2: 4 minutes  
        // Retry 3: 8 minutes
        return (int) Math.pow(2, retryCount);
    }
}

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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class SubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriberProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
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

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();
        
        // Set subscription date if not already set (for new subscribers)
        if (entity.getSubscriptionDate() == null) {
            entity.setSubscriptionDate(LocalDateTime.now());
        }
        
        // Initialize usage count if not set
        if (entity.getIsActive() == null) {
            entity.setIsActive(false); // Default to false for pending verification
        }
        
        // Set default preferences if not provided
        if (entity.getPreferences() == null) {
            Map<String, Object> defaultPreferences = new HashMap<>();
            defaultPreferences.put("preferredDay", "MONDAY");
            defaultPreferences.put("timezone", "UTC");
            entity.setPreferences(defaultPreferences);
        }
        
        // Generate verification token for new subscribers
        if (entity.getId() == null) {
            entity.setId(entity.getEmail()); // Use email as ID
            // In a real implementation, we would generate and store a verification token
            // and send a verification email here
            logger.info("New subscriber registered: {}", entity.getEmail());
        }
        
        logger.info("Processed subscriber: {} with state transition", entity.getEmail());
        return entity;
    }
}

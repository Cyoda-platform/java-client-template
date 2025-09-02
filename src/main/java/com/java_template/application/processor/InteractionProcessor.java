package com.java_template.application.processor;

import com.java_template.application.entity.interaction.version_1.Interaction;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class InteractionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InteractionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    
    @Autowired
    private EntityService entityService;

    public InteractionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Interaction for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Interaction.class)
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

    private boolean isValidEntity(Interaction entity) {
        return entity != null && entity.isValid();
    }

    private Interaction processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Interaction> context) {
        Interaction entity = context.entity();
        
        // Generate ID if not set
        if (entity.getId() == null) {
            entity.setId("interaction-" + UUID.randomUUID().toString().substring(0, 8));
        }
        
        // Set interaction date if not already set
        if (entity.getInteractionDate() == null) {
            entity.setInteractionDate(LocalDateTime.now());
        }
        
        // Initialize metadata if not provided
        if (entity.getMetadata() == null) {
            entity.setMetadata(new HashMap<>());
        }
        
        // Validate interaction type
        if (entity.getInteractionType() != null) {
            String type = entity.getInteractionType().toUpperCase();
            if (!isValidInteractionType(type)) {
                logger.warn("Invalid interaction type: {}, defaulting to EMAIL_OPENED", type);
                entity.setInteractionType("EMAIL_OPENED");
            } else {
                entity.setInteractionType(type);
            }
        }
        
        // Process interaction for reporting
        processInteractionForReporting(entity);
        
        // Update subscriber engagement metrics
        updateSubscriberEngagement(entity);
        
        logger.info("Processed interaction: {} of type: {} for subscriber: {}", 
                   entity.getId(), entity.getInteractionType(), entity.getSubscriberId());
        return entity;
    }
    
    private boolean isValidInteractionType(String type) {
        return "EMAIL_OPENED".equals(type) || 
               "EMAIL_CLICKED".equals(type) || 
               "UNSUBSCRIBED".equals(type);
    }
    
    private void processInteractionForReporting(Interaction interaction) {
        // In a real implementation, this would:
        // 1. Update reporting metrics
        // 2. Calculate engagement scores
        // 3. Generate analytics data
        // 4. Update campaign statistics
        
        Map<String, Object> metadata = interaction.getMetadata();
        metadata.put("processedDate", LocalDateTime.now().toString());
        metadata.put("reportingStatus", "processed");
        
        logger.info("Processed interaction for reporting: {}", interaction.getId());
    }
    
    private void updateSubscriberEngagement(Interaction interaction) {
        try {
            // Find the subscriber and update engagement metrics
            EntityResponse<Subscriber> subscriberResponse = entityService.findByBusinessId(
                Subscriber.class, 
                Subscriber.ENTITY_NAME, 
                Subscriber.ENTITY_VERSION, 
                interaction.getSubscriberId(), 
                "email"
            );
            
            Subscriber subscriber = subscriberResponse.getData();
            
            // Update subscriber preferences with engagement data
            Map<String, Object> preferences = subscriber.getPreferences();
            if (preferences == null) {
                preferences = new HashMap<>();
                subscriber.setPreferences(preferences);
            }
            
            // Track engagement metrics
            String engagementKey = "lastInteraction";
            preferences.put(engagementKey, interaction.getInteractionDate().toString());
            preferences.put("lastInteractionType", interaction.getInteractionType());
            
            // Update engagement score based on interaction type
            updateEngagementScore(preferences, interaction.getInteractionType());
            
            entityService.update(subscriberResponse.getMetadata().getId(), subscriber, null);
            logger.info("Updated subscriber engagement for: {}", interaction.getSubscriberId());
            
        } catch (Exception e) {
            logger.error("Error updating subscriber engagement: {}", e.getMessage(), e);
        }
    }
    
    private void updateEngagementScore(Map<String, Object> preferences, String interactionType) {
        double currentScore = preferences.containsKey("engagementScore") ? 
            ((Number) preferences.get("engagementScore")).doubleValue() : 0.5;
        
        // Adjust score based on interaction type
        switch (interactionType) {
            case "EMAIL_OPENED":
                currentScore = Math.min(1.0, currentScore + 0.1);
                break;
            case "EMAIL_CLICKED":
                currentScore = Math.min(1.0, currentScore + 0.2);
                break;
            case "UNSUBSCRIBED":
                currentScore = 0.0;
                break;
        }
        
        preferences.put("engagementScore", currentScore);
    }
}

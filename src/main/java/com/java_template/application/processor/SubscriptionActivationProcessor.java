package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * SubscriptionActivationProcessor
 * 
 * Activates subscription and sets default preferences.
 * Used in Subscriber workflow transitions: activate_subscription, reactivate
 */
@Component
public class SubscriptionActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubscriptionActivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber activation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Subscriber.class)
                .validate(this::isValidEntityWithMetadata, "Invalid Subscriber entity")
                .map(this::processSubscriptionActivation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Subscriber> entityWithMetadata) {
        Subscriber entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for subscription activation
     */
    private EntityWithMetadata<Subscriber> processSubscriptionActivation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subscriber> context) {

        EntityWithMetadata<Subscriber> entityWithMetadata = context.entityResponse();
        Subscriber subscriber = entityWithMetadata.entity();

        logger.info("Activating subscription for Subscriber: {}", subscriber.getSubscriberId());

        try {
            // Set subscription as active
            subscriber.setIsActive(true);
            
            // Set subscription timestamp
            subscriber.setSubscribedAt(LocalDateTime.now());

            // Set default email preferences if not already set
            if (subscriber.getEmailPreferences() == null) {
                Subscriber.EmailPreferences defaultPreferences = new Subscriber.EmailPreferences();
                defaultPreferences.setFrequency("IMMEDIATE");
                defaultPreferences.setFormat("HTML");
                defaultPreferences.setTopics(Arrays.asList("housing", "analysis", "reports"));
                
                subscriber.setEmailPreferences(defaultPreferences);
                
                logger.debug("Set default email preferences for Subscriber: {}", subscriber.getSubscriberId());
            } else {
                // Validate and set defaults for missing preference fields
                Subscriber.EmailPreferences preferences = subscriber.getEmailPreferences();
                
                if (preferences.getFrequency() == null || preferences.getFrequency().trim().isEmpty()) {
                    preferences.setFrequency("IMMEDIATE");
                }
                
                if (preferences.getFormat() == null || preferences.getFormat().trim().isEmpty()) {
                    preferences.setFormat("HTML");
                }
                
                if (preferences.getTopics() == null || preferences.getTopics().isEmpty()) {
                    preferences.setTopics(Arrays.asList("housing", "analysis", "reports"));
                }
                
                logger.debug("Updated email preferences for Subscriber: {}", subscriber.getSubscriberId());
            }

            logger.info("Subscription activated successfully for Subscriber: {} with email: {}", 
                       subscriber.getSubscriberId(), subscriber.getEmail());

        } catch (Exception e) {
            logger.error("Failed to activate subscription for Subscriber: {}", subscriber.getSubscriberId(), e);
            throw new RuntimeException("Failed to activate subscription: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }
}

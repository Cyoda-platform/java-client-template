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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * SubscriberValidationProcessor - Validates and processes subscriber data
 * 
 * This processor validates subscriber information, normalizes email addresses,
 * sets up default preferences, and ensures data quality.
 */
@Component
public class SubscriberValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    public SubscriberValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber validation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Subscriber.class)
                .validate(this::isValidEntityWithMetadata, "Invalid Subscriber entity wrapper")
                .map(this::processValidationLogic)
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
        UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && technicalId != null;
    }

    /**
     * Main business logic for subscriber validation and processing
     */
    private EntityWithMetadata<Subscriber> processValidationLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Subscriber> context) {

        EntityWithMetadata<Subscriber> entityWithMetadata = context.entityResponse();
        Subscriber entity = entityWithMetadata.entity();

        logger.debug("Validating subscriber: {}", entity.getSubscriberId());

        // Validate and normalize email
        validateAndNormalizeEmail(entity);
        
        // Validate and normalize name
        validateAndNormalizeName(entity);
        
        // Set up subscription data
        setupSubscriptionData(entity);
        
        // Initialize preferences if needed
        initializePreferences(entity);
        
        // Initialize tracking fields
        initializeTrackingFields(entity);

        // Update timestamps
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }

        logger.info("Subscriber {} validated successfully", entity.getSubscriberId());

        return entityWithMetadata;
    }

    /**
     * Validates and normalizes email address
     */
    private void validateAndNormalizeEmail(Subscriber entity) {
        String email = entity.getEmail();
        
        if (email != null) {
            // Normalize email (trim and lowercase)
            email = email.trim().toLowerCase();
            entity.setEmail(email);
            
            // Validate email format
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                logger.warn("Invalid email format for subscriber {}: {}", 
                           entity.getSubscriberId(), email);
                // In a real implementation, you might throw an exception or set an error flag
            } else {
                logger.debug("Email validated and normalized for subscriber: {}", entity.getSubscriberId());
            }
        }
    }

    /**
     * Validates and normalizes subscriber name
     */
    private void validateAndNormalizeName(Subscriber entity) {
        String name = entity.getName();
        
        if (name != null) {
            // Normalize name (trim and proper case)
            name = name.trim();
            if (!name.isEmpty()) {
                // Simple name normalization - capitalize first letter of each word
                String[] words = name.split("\\s+");
                StringBuilder normalizedName = new StringBuilder();
                
                for (int i = 0; i < words.length; i++) {
                    if (i > 0) normalizedName.append(" ");
                    String word = words[i].toLowerCase();
                    if (word.length() > 0) {
                        normalizedName.append(Character.toUpperCase(word.charAt(0)));
                        if (word.length() > 1) {
                            normalizedName.append(word.substring(1));
                        }
                    }
                }
                
                entity.setName(normalizedName.toString());
                logger.debug("Name normalized for subscriber: {}", entity.getSubscriberId());
            }
        }
    }

    /**
     * Sets up subscription data with defaults
     */
    private void setupSubscriptionData(Subscriber entity) {
        // Set subscription date if not provided
        if (entity.getSubscriptionDate() == null) {
            entity.setSubscriptionDate(LocalDateTime.now());
        }
        
        // Set active status if not provided
        if (entity.getIsActive() == null) {
            entity.setIsActive(true);
        }
        
        logger.debug("Subscription data set up for subscriber: {}", entity.getSubscriberId());
    }

    /**
     * Initializes subscriber preferences with defaults
     */
    private void initializePreferences(Subscriber entity) {
        if (entity.getPreferences() == null) {
            Subscriber.SubscriberPreferences preferences = new Subscriber.SubscriberPreferences();
            
            // Set default preferences
            preferences.setPreferredTimeZone("UTC");
            preferences.setEmailFormat("HTML");
            preferences.setReceiveWeeklyDigest(true);
            preferences.setReceiveSpecialOffers(false);
            preferences.setFrequency("WEEKLY");
            
            entity.setPreferences(preferences);
            logger.debug("Default preferences initialized for subscriber: {}", entity.getSubscriberId());
        } else {
            // Validate and set defaults for missing preference fields
            Subscriber.SubscriberPreferences prefs = entity.getPreferences();
            
            if (prefs.getPreferredTimeZone() == null || prefs.getPreferredTimeZone().trim().isEmpty()) {
                prefs.setPreferredTimeZone("UTC");
            }
            
            if (prefs.getEmailFormat() == null || prefs.getEmailFormat().trim().isEmpty()) {
                prefs.setEmailFormat("HTML");
            }
            
            if (prefs.getReceiveWeeklyDigest() == null) {
                prefs.setReceiveWeeklyDigest(true);
            }
            
            if (prefs.getReceiveSpecialOffers() == null) {
                prefs.setReceiveSpecialOffers(false);
            }
            
            if (prefs.getFrequency() == null || prefs.getFrequency().trim().isEmpty()) {
                prefs.setFrequency("WEEKLY");
            }
            
            logger.debug("Preferences validated and defaults set for subscriber: {}", entity.getSubscriberId());
        }
    }

    /**
     * Initializes tracking fields with defaults
     */
    private void initializeTrackingFields(Subscriber entity) {
        // Initialize email tracking fields if not set
        if (entity.getTotalEmailsReceived() == null) {
            entity.setTotalEmailsReceived(0);
        }
        
        // lastEmailSent will be set by EmailCampaignProcessor when emails are sent
        
        logger.debug("Tracking fields initialized for subscriber: {}", entity.getSubscriberId());
    }
}

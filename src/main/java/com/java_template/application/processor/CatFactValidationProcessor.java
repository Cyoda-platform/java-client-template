package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
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

/**
 * CatFactValidationProcessor - Validates and processes cat fact content
 * 
 * This processor validates cat fact content, updates metadata,
 * and ensures data quality before the fact can be used in campaigns.
 */
@Component
public class CatFactValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CatFactValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact validation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CatFact.class)
                .validate(this::isValidEntityWithMetadata, "Invalid CatFact entity wrapper")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<CatFact> entityWithMetadata) {
        CatFact entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for cat fact validation and processing
     */
    private EntityWithMetadata<CatFact> processValidationLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CatFact> context) {

        EntityWithMetadata<CatFact> entityWithMetadata = context.entityResponse();
        CatFact entity = entityWithMetadata.entity();

        logger.debug("Validating cat fact: {}", entity.getFactId());

        // Validate and clean content
        validateAndCleanContent(entity);
        
        // Update content metrics
        updateContentMetrics(entity);
        
        // Validate metadata
        validateMetadata(entity);

        // Update timestamps
        entity.setUpdatedAt(LocalDateTime.now());

        logger.info("CatFact {} validated successfully", entity.getFactId());

        return entityWithMetadata;
    }

    /**
     * Validates and cleans the cat fact content
     */
    private void validateAndCleanContent(CatFact entity) {
        String content = entity.getContent();
        
        if (content != null) {
            // Clean and normalize content
            content = content.trim();
            content = content.replaceAll("\\s+", " "); // Normalize whitespace
            
            // Remove any potentially harmful content (basic sanitization)
            content = content.replaceAll("[<>\"']", "");
            
            entity.setContent(content);
            entity.setContentLength(content.length());
            
            logger.debug("Content cleaned and normalized for fact: {}", entity.getFactId());
        }
    }

    /**
     * Updates content metrics and quality scoring
     */
    private void updateContentMetrics(CatFact entity) {
        String content = entity.getContent();
        
        if (content != null) {
            // Update content length
            entity.setContentLength(content.length());
            
            // Update quality score in metadata
            if (entity.getMetadata() != null) {
                Double qualityScore = calculateContentQuality(content);
                entity.getMetadata().setQualityScore(qualityScore);
                
                // Mark as verified if quality is good
                entity.getMetadata().setIsVerified(qualityScore >= 0.7);
                
                logger.debug("Quality score updated to {} for fact: {}", 
                           qualityScore, entity.getFactId());
            }
        }
    }

    /**
     * Validates and initializes metadata if needed
     */
    private void validateMetadata(CatFact entity) {
        if (entity.getMetadata() == null) {
            // Initialize metadata if missing
            CatFact.CatFactMetadata metadata = new CatFact.CatFactMetadata();
            metadata.setQualityScore(0.5);
            metadata.setIsVerified(false);
            metadata.setRetrievalMethod("MANUAL");
            entity.setMetadata(metadata);
            
            logger.debug("Initialized metadata for fact: {}", entity.getFactId());
        }
    }

    /**
     * Calculates content quality score based on various factors
     */
    private Double calculateContentQuality(String content) {
        if (content == null || content.trim().isEmpty()) {
            return 0.0;
        }
        
        double score = 0.3; // Base score
        
        // Length scoring (optimal range: 50-200 characters)
        int length = content.length();
        if (length >= 50 && length <= 200) {
            score += 0.4;
        } else if (length > 200 && length <= 300) {
            score += 0.2;
        } else if (length < 50 && length >= 20) {
            score += 0.1;
        }
        
        // Content relevance scoring
        String lowerContent = content.toLowerCase();
        if (lowerContent.contains("cat") || lowerContent.contains("feline")) {
            score += 0.2;
        }
        
        // Grammar and structure scoring (basic)
        if (content.matches(".*[.!?]$")) { // Ends with punctuation
            score += 0.1;
        }
        
        // Avoid repetitive or low-quality content
        if (content.split("\\s+").length < 3) { // Too few words
            score -= 0.2;
        }
        
        return Math.max(0.0, Math.min(1.0, score)); // Clamp between 0 and 1
    }
}

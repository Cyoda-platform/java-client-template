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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * CatFactValidationProcessor - Validates and prepares cat fact for use
 * 
 * Input: CatFact entity in RETRIEVED state
 * Purpose: Validate and prepare cat fact for use
 * Output: CatFact entity in READY state
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
                .validate(this::isValidEntityWithMetadata, "Invalid cat fact for validation")
                .map(this::processCatFactValidation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for validation
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<CatFact> entityWithMetadata) {
        CatFact catFact = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();
        
        return catFact != null && 
               catFact.isValid() && 
               "retrieved".equalsIgnoreCase(currentState) &&
               entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic for cat fact validation
     */
    private EntityWithMetadata<CatFact> processCatFactValidation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CatFact> context) {

        EntityWithMetadata<CatFact> entityWithMetadata = context.entityResponse();
        CatFact catFact = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Validating cat fact: {} in state: {}", catFact.getFactId(), currentState);

        // Verify cat fact is in RETRIEVED state
        if (!"retrieved".equalsIgnoreCase(currentState)) {
            logger.warn("CatFact {} is not in RETRIEVED state, current state: {}", 
                       catFact.getFactId(), currentState);
            return entityWithMetadata;
        }

        // Perform validation checks
        if (!performContentValidation(catFact)) {
            logger.warn("CatFact {} failed content validation", catFact.getFactId());
            return entityWithMetadata;
        }

        logger.info("CatFact {} validated successfully", catFact.getFactId());

        // Return updated entity (state transition will be handled by workflow)
        return entityWithMetadata;
    }

    /**
     * Performs content validation on the cat fact
     */
    private boolean performContentValidation(CatFact catFact) {
        // Verify fact text is not empty
        if (catFact.getText() == null || catFact.getText().trim().isEmpty()) {
            logger.warn("Cat fact text is empty");
            return false;
        }

        // Verify length matches actual text length
        if (!catFact.getLength().equals(catFact.getText().length())) {
            logger.warn("Cat fact length mismatch: expected {}, actual {}", 
                       catFact.getLength(), catFact.getText().length());
            return false;
        }

        // Check for inappropriate content (basic filtering)
        if (containsInappropriateContent(catFact.getText())) {
            logger.warn("Cat fact contains inappropriate content");
            return false;
        }

        return true;
    }

    /**
     * Basic content filtering for inappropriate content
     */
    private boolean containsInappropriateContent(String text) {
        // Simple inappropriate content check
        String[] inappropriateWords = {"spam", "advertisement", "buy now", "click here"};
        String lowerText = text.toLowerCase();
        
        for (String word : inappropriateWords) {
            if (lowerText.contains(word)) {
                return true;
            }
        }
        
        return false;
    }
}

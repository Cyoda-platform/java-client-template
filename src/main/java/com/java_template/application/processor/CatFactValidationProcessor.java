package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Processor for cat fact validation workflow transition.
 * Handles the validate transition (retrieved → validated).
 * 
 * Business Logic:
 * - Validates fact text is not empty
 * - Validates fact length is between 10 and 500 characters
 * - Checks for inappropriate content (profanity filter)
 * - Validates text encoding (UTF-8)
 * - Sets validation status and date
 */
@Component
public class CatFactValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactValidationProcessor.class);
    private static final int MIN_FACT_LENGTH = 10;
    private static final int MAX_FACT_LENGTH = 500;
    
    // Simple profanity filter - in production, use a more sophisticated solution
    private static final List<String> PROFANITY_WORDS = Arrays.asList(
        "damn", "hell", "crap", "stupid", "idiot", "hate"
    );
    
    // Pattern for invalid characters (non-printable, control characters except newlines)
    private static final Pattern INVALID_CHARS_PATTERN = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    
    private final ProcessorSerializer serializer;

    public CatFactValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.debug("CatFactValidationProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing cat fact validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFact.class)
            .validate(this::validateCatFact, "Cat fact validation failed")
            .map(ctx -> {
                CatFact catFact = ctx.entity();
                
                // Set validation metadata
                catFact.setCategory("validated");
                
                logger.info("Cat fact validation passed: {} characters", catFact.getLength());
                return catFact;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "CatFactValidationProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates cat fact content quality and appropriateness.
     * 
     * @param catFact The cat fact to validate
     * @return true if valid, false otherwise
     */
    private boolean validateCatFact(CatFact catFact) {
        if (catFact == null) {
            logger.warn("Validation failed: CatFact is null");
            return false;
        }
        
        String factText = catFact.getFactText();
        
        // Check if fact text is empty
        if (factText == null || factText.trim().isEmpty()) {
            logger.warn("Validation failed: Fact text is empty");
            return false;
        }
        
        factText = factText.trim();
        
        // Check fact length
        if (factText.length() < MIN_FACT_LENGTH) {
            logger.warn("Validation failed: Fact too short ({} chars, minimum {})", 
                       factText.length(), MIN_FACT_LENGTH);
            return false;
        }
        
        if (factText.length() > MAX_FACT_LENGTH) {
            logger.warn("Validation failed: Fact too long ({} chars, maximum {})", 
                       factText.length(), MAX_FACT_LENGTH);
            return false;
        }
        
        // Check for inappropriate content
        if (containsProfanity(factText)) {
            logger.warn("Validation failed: Inappropriate content detected");
            return false;
        }
        
        // Check for invalid characters
        if (containsInvalidCharacters(factText)) {
            logger.warn("Validation failed: Invalid characters detected");
            return false;
        }
        
        // Validate text encoding
        if (!isValidUtf8(factText)) {
            logger.warn("Validation failed: Invalid UTF-8 encoding");
            return false;
        }
        
        // Verify length matches actual text length
        if (catFact.getLength() == null || !catFact.getLength().equals(factText.length())) {
            logger.warn("Validation failed: Length mismatch (stored: {}, actual: {})", 
                       catFact.getLength(), factText.length());
            return false;
        }
        
        logger.debug("Cat fact validation passed: {}", factText.substring(0, Math.min(50, factText.length())));
        return true;
    }

    /**
     * Checks if the text contains profanity.
     */
    private boolean containsProfanity(String text) {
        String lowerText = text.toLowerCase();
        return PROFANITY_WORDS.stream().anyMatch(lowerText::contains);
    }

    /**
     * Checks if the text contains invalid characters.
     */
    private boolean containsInvalidCharacters(String text) {
        return INVALID_CHARS_PATTERN.matcher(text).find();
    }

    /**
     * Validates that the text is valid UTF-8.
     */
    private boolean isValidUtf8(String text) {
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            String reconstructed = new String(bytes, StandardCharsets.UTF_8);
            return text.equals(reconstructed);
        } catch (Exception e) {
            return false;
        }
    }
}

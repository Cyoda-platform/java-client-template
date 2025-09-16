package com.java_template.application.criterion;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Criterion to validate cat fact content quality and appropriateness.
 * Handles the validate transition (retrieved → validated).
 * 
 * Validation Logic:
 * - Checks if fact text is not empty
 * - Validates fact length is between 10 and 500 characters
 * - Checks for inappropriate content (profanity filter)
 * - Validates text encoding (UTF-8)
 * - Checks for invalid characters
 * - Verifies fact is not a duplicate
 */
@Component
public class CatFactValidationCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(CatFactValidationCriterion.class);
    private static final int MIN_FACT_LENGTH = 10;
    private static final int MAX_FACT_LENGTH = 500;
    
    // Simple profanity filter - in production, use a more sophisticated solution
    private static final List<String> PROFANITY_WORDS = Arrays.asList(
        "damn", "hell", "crap", "stupid", "idiot", "hate", "kill", "die", "death"
    );
    
    // Pattern for invalid characters (non-printable, control characters except newlines)
    private static final Pattern INVALID_CHARS_PATTERN = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    
    private final CriterionSerializer serializer;

    public CatFactValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.debug("CatFactValidationCriterion initialized");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking cat fact validation criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(CatFact.class, ctx -> this.evaluateCatFactValidation(ctx.entity()))
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "CatFactValidationCriterion".equals(opSpec.operationName());
    }

    /**
     * Evaluates whether a cat fact meets validation criteria.
     * 
     * @param catFact The cat fact to evaluate
     * @return EvaluationOutcome indicating whether validation criteria are met
     */
    private EvaluationOutcome evaluateCatFactValidation(CatFact catFact) {
        if (catFact == null) {
            return EvaluationOutcome.fail("Cat fact is null");
        }

        String factText = catFact.getFactText();
        
        // Check if fact text is empty
        if (factText == null || factText.trim().isEmpty()) {
            return EvaluationOutcome.fail("Fact text is empty");
        }
        
        factText = factText.trim();
        
        // Check fact length
        if (factText.length() < MIN_FACT_LENGTH) {
            return EvaluationOutcome.fail(String.format("Fact text too short (%d characters, minimum %d)", 
                                                       factText.length(), MIN_FACT_LENGTH));
        }
        
        if (factText.length() > MAX_FACT_LENGTH) {
            return EvaluationOutcome.fail(String.format("Fact text too long (%d characters, maximum %d)", 
                                                       factText.length(), MAX_FACT_LENGTH));
        }
        
        // Check for inappropriate content
        if (containsProfanity(factText)) {
            return EvaluationOutcome.fail("Inappropriate content detected");
        }
        
        // Check for invalid characters
        if (containsInvalidCharacters(factText)) {
            return EvaluationOutcome.fail("Invalid characters in fact text");
        }
        
        // Validate text encoding
        if (!isValidUtf8(factText)) {
            return EvaluationOutcome.fail("Invalid UTF-8 encoding");
        }
        
        // Verify length matches actual text length
        if (catFact.getLength() != null && !catFact.getLength().equals(factText.length())) {
            return EvaluationOutcome.fail(String.format("Length mismatch (stored: %d, actual: %d)", 
                                                       catFact.getLength(), factText.length()));
        }
        
        // Check if fact is already used
        if (catFact.getIsUsed() != null && catFact.getIsUsed()) {
            return EvaluationOutcome.fail("Fact is already marked as used");
        }
        
        // All validation criteria passed
        logger.debug("Cat fact validation criteria met: {}", 
                    factText.substring(0, Math.min(50, factText.length())));
        return EvaluationOutcome.success();
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

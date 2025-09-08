package com.java_template.application.criterion;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * CatFactValidityCriterion - Validates cat fact content and readiness for use
 * 
 * Purpose: Validate cat fact content and readiness for use
 * Input: CatFact entity
 * Output: Boolean (true if valid, false otherwise)
 * 
 * Use Cases:
 * - RETRIEVED → READY transition
 */
@Component
public class CatFactValidityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CatFactValidityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking CatFact validity criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(CatFact.class, this::validateCatFact)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the cat fact entity
     */
    private EvaluationOutcome validateCatFact(CriterionSerializer.CriterionEntityEvaluationContext<CatFact> context) {
        CatFact catFact = context.entityWithMetadata().entity();

        // Check if cat fact is null (structural validation)
        if (catFact == null) {
            logger.warn("CatFact is null");
            return EvaluationOutcome.fail("CatFact entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check basic entity validity
        if (!catFact.isValid()) {
            logger.warn("CatFact {} is not valid", catFact.getFactId());
            return EvaluationOutcome.fail("CatFact entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate text content
        if (catFact.getText() == null || catFact.getText().trim().isEmpty()) {
            logger.warn("CatFact {} has empty text", catFact.getFactId());
            return EvaluationOutcome.fail("Cat fact text is empty", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate length matches actual text length
        if (!catFact.getLength().equals(catFact.getText().length())) {
            logger.warn("CatFact {} length mismatch: expected {}, actual {}", 
                       catFact.getFactId(), catFact.getLength(), catFact.getText().length());
            return EvaluationOutcome.fail("Text length mismatch", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate text length is within acceptable range
        if (catFact.getText().length() < 10 || catFact.getText().length() > 500) {
            logger.warn("CatFact {} text length {} is outside acceptable range (10-500)", 
                       catFact.getFactId(), catFact.getText().length());
            return EvaluationOutcome.fail("Text length outside acceptable range", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate retrieved date
        if (catFact.getRetrievedDate() == null) {
            logger.warn("CatFact {} has missing retrieved date", catFact.getFactId());
            return EvaluationOutcome.fail("Missing retrieved date", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Retrieved date cannot be in the future
        if (catFact.getRetrievedDate().isAfter(LocalDateTime.now())) {
            logger.warn("CatFact {} has future retrieved date", catFact.getFactId());
            return EvaluationOutcome.fail("Retrieved date cannot be in the future", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check for inappropriate content
        if (containsInappropriateContent(catFact.getText())) {
            logger.warn("CatFact {} contains inappropriate content", catFact.getFactId());
            return EvaluationOutcome.fail("Contains inappropriate content", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("CatFact {} validation passed", catFact.getFactId());
        return EvaluationOutcome.success();
    }

    /**
     * Checks for inappropriate content in the cat fact text
     */
    private boolean containsInappropriateContent(String text) {
        if (text == null) {
            return false;
        }

        String lowerText = text.toLowerCase();
        
        // List of inappropriate words/phrases
        String[] inappropriateWords = {
            "spam", "advertisement", "buy now", "click here", "free money",
            "viagra", "casino", "lottery", "winner", "congratulations"
        };
        
        for (String word : inappropriateWords) {
            if (lowerText.contains(word)) {
                return true;
            }
        }
        
        return false;
    }
}

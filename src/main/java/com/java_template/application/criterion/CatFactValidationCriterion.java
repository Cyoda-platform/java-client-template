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

/**
 * CatFactValidationCriterion - Validates cat fact content quality and suitability
 * 
 * This criterion validates cat fact content for quality, appropriateness,
 * and suitability for email campaigns.
 */
@Component
public class CatFactValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Content quality thresholds
    private static final int MIN_CONTENT_LENGTH = 10;
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final double MIN_QUALITY_SCORE = 0.5;

    public CatFactValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking CatFact validation criteria for request: {}", request.getId());
        
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
        CatFact entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("CatFact entity is null");
            return EvaluationOutcome.fail("CatFact entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate required fields
        EvaluationOutcome requiredFieldsResult = validateRequiredFields(entity);
        if (!requiredFieldsResult.isSuccess()) {
            return requiredFieldsResult;
        }

        // Validate content quality
        EvaluationOutcome contentQualityResult = validateContentQuality(entity);
        if (!contentQualityResult.isSuccess()) {
            return contentQualityResult;
        }

        // Validate content appropriateness
        EvaluationOutcome appropriatenessResult = validateContentAppropriateness(entity);
        if (!appropriatenessResult.isSuccess()) {
            return appropriatenessResult;
        }

        // Validate metadata if present
        if (entity.getMetadata() != null) {
            EvaluationOutcome metadataResult = validateMetadata(entity);
            if (!metadataResult.isSuccess()) {
                return metadataResult;
            }
        }

        logger.debug("CatFact {} passed all validation criteria", entity.getFactId());
        return EvaluationOutcome.success();
    }

    /**
     * Validates required fields are present and not empty
     */
    private EvaluationOutcome validateRequiredFields(CatFact entity) {
        if (entity.getFactId() == null || entity.getFactId().trim().isEmpty()) {
            logger.warn("Fact ID is missing or empty");
            return EvaluationOutcome.fail("Fact ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getContent() == null || entity.getContent().trim().isEmpty()) {
            logger.warn("Content is missing for cat fact: {}", entity.getFactId());
            return EvaluationOutcome.fail("Cat fact content is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getSource() == null || entity.getSource().trim().isEmpty()) {
            logger.warn("Source is missing for cat fact: {}", entity.getFactId());
            return EvaluationOutcome.fail("Cat fact source is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getRetrievedDate() == null) {
            logger.warn("Retrieved date is missing for cat fact: {}", entity.getFactId());
            return EvaluationOutcome.fail("Retrieved date is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getIsUsed() == null) {
            logger.warn("Used status is missing for cat fact: {}", entity.getFactId());
            return EvaluationOutcome.fail("Used status is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates content quality based on length, structure, and relevance
     */
    private EvaluationOutcome validateContentQuality(CatFact entity) {
        String content = entity.getContent().trim();

        // Check content length
        if (content.length() < MIN_CONTENT_LENGTH) {
            logger.warn("Content too short for cat fact {}: {} characters", 
                       entity.getFactId(), content.length());
            return EvaluationOutcome.fail("Cat fact content is too short (minimum " + MIN_CONTENT_LENGTH + " characters)", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (content.length() > MAX_CONTENT_LENGTH) {
            logger.warn("Content too long for cat fact {}: {} characters", 
                       entity.getFactId(), content.length());
            return EvaluationOutcome.fail("Cat fact content is too long (maximum " + MAX_CONTENT_LENGTH + " characters)", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check for cat-related content
        String lowerContent = content.toLowerCase();
        if (!lowerContent.contains("cat") && !lowerContent.contains("feline") && 
            !lowerContent.contains("kitten") && !lowerContent.contains("kitty")) {
            logger.warn("Content doesn't appear to be cat-related for fact: {}", entity.getFactId());
            return EvaluationOutcome.fail("Content must be cat-related", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check for basic sentence structure
        if (!content.matches(".*[.!?]$")) {
            logger.warn("Content doesn't end with proper punctuation for fact: {}", entity.getFactId());
            return EvaluationOutcome.fail("Content should end with proper punctuation", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check word count (should have at least 3 words)
        String[] words = content.split("\\s+");
        if (words.length < 3) {
            logger.warn("Content has too few words for fact {}: {} words", 
                       entity.getFactId(), words.length);
            return EvaluationOutcome.fail("Content should have at least 3 words", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates content appropriateness for email campaigns
     */
    private EvaluationOutcome validateContentAppropriateness(CatFact entity) {
        String content = entity.getContent().toLowerCase();

        // Check for inappropriate content
        String[] inappropriateWords = {
            "death", "die", "kill", "violence", "abuse", "cruel", "torture", 
            "pain", "suffer", "blood", "injury", "sick", "disease"
        };

        for (String word : inappropriateWords) {
            if (content.contains(word)) {
                logger.warn("Inappropriate content detected in fact {}: contains '{}'", 
                           entity.getFactId(), word);
                return EvaluationOutcome.fail("Content contains inappropriate material for email campaigns", 
                                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // Check for overly technical or complex content
        if (content.contains("scientific") && content.contains("study") && content.contains("research")) {
            // This is acceptable, but let's check if it's too complex
            String[] complexTerms = {"hypothesis", "methodology", "statistical", "correlation", "coefficient"};
            int complexTermCount = 0;
            for (String term : complexTerms) {
                if (content.contains(term)) {
                    complexTermCount++;
                }
            }
            
            if (complexTermCount > 2) {
                logger.warn("Content may be too technical for general audience: {}", entity.getFactId());
                return EvaluationOutcome.fail("Content may be too technical for general email audience", 
                                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates metadata quality and consistency
     */
    private EvaluationOutcome validateMetadata(CatFact entity) {
        CatFact.CatFactMetadata metadata = entity.getMetadata();

        // Check quality score if present
        if (metadata.getQualityScore() != null) {
            if (metadata.getQualityScore() < 0.0 || metadata.getQualityScore() > 1.0) {
                logger.warn("Invalid quality score for fact {}: {}", 
                           entity.getFactId(), metadata.getQualityScore());
                return EvaluationOutcome.fail("Quality score must be between 0.0 and 1.0", 
                                            StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            if (metadata.getQualityScore() < MIN_QUALITY_SCORE) {
                logger.warn("Quality score too low for fact {}: {}", 
                           entity.getFactId(), metadata.getQualityScore());
                return EvaluationOutcome.fail("Quality score is below minimum threshold (" + MIN_QUALITY_SCORE + ")", 
                                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // Validate retrieval method if present
        if (metadata.getRetrievalMethod() != null) {
            String method = metadata.getRetrievalMethod().toUpperCase();
            if (!method.equals("API") && !method.equals("SCRAPING") && !method.equals("MANUAL")) {
                logger.warn("Invalid retrieval method for fact {}: {}", 
                           entity.getFactId(), metadata.getRetrievalMethod());
                return EvaluationOutcome.fail("Retrieval method must be API, SCRAPING, or MANUAL", 
                                            StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}

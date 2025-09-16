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

import java.util.Arrays;
import java.util.List;

/**
 * Criterion to determine if a validated fact should be rejected for business reasons.
 * Handles the reject transition (validated → archived).
 * 
 * Validation Logic:
 * - Checks if category is "medical" and not verified medical fact
 * - Checks for controversial content
 * - Checks if source is unreliable
 * - Returns success if fact should be rejected
 */
@Component
public class CatFactRejectionCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(CatFactRejectionCriterion.class);
    
    // List of controversial topics/keywords
    private static final List<String> CONTROVERSIAL_KEYWORDS = Arrays.asList(
        "politics", "religion", "war", "violence", "controversial", "debate", 
        "argument", "fight", "conflict", "discrimination"
    );
    
    // List of unreliable sources
    private static final List<String> UNRELIABLE_SOURCES = Arrays.asList(
        "unreliable_source", "unknown", "unverified", "social_media", "rumor"
    );
    
    // Medical keywords that require verification
    private static final List<String> MEDICAL_KEYWORDS = Arrays.asList(
        "disease", "illness", "sick", "medicine", "treatment", "cure", "therapy",
        "vaccine", "drug", "medication", "health", "medical", "doctor", "vet"
    );
    
    private final CriterionSerializer serializer;

    public CatFactRejectionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.debug("CatFactRejectionCriterion initialized");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking cat fact rejection criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(CatFact.class, ctx -> this.evaluateCatFactRejection(ctx.entity()))
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "CatFactRejectionCriterion".equals(opSpec.operationName());
    }

    /**
     * Evaluates whether a cat fact should be rejected for business reasons.
     * 
     * @param catFact The cat fact to evaluate
     * @return EvaluationOutcome indicating whether fact should be rejected
     */
    private EvaluationOutcome evaluateCatFactRejection(CatFact catFact) {
        if (catFact == null) {
            return EvaluationOutcome.fail("Cat fact is null");
        }

        String factText = catFact.getFactText();
        if (factText == null || factText.trim().isEmpty()) {
            return EvaluationOutcome.fail("Fact text is empty");
        }

        String category = catFact.getCategory();
        String source = catFact.getSource();
        
        // Check for unverified medical claims
        if ("medical".equals(category) && !isVerifiedMedicalFact(catFact)) {
            logger.info("Rejecting cat fact: unverified medical claim");
            return EvaluationOutcome.success(); // Criteria met for rejection
        }
        
        // Check for medical content without medical category
        if (containsMedicalContent(factText) && !"medical".equals(category)) {
            logger.info("Rejecting cat fact: medical content without proper categorization");
            return EvaluationOutcome.success(); // Criteria met for rejection
        }
        
        // Check for controversial content
        if (containsControversialContent(factText)) {
            logger.info("Rejecting cat fact: controversial content detected");
            return EvaluationOutcome.success(); // Criteria met for rejection
        }
        
        // Check for unreliable source
        if (isUnreliableSource(source)) {
            logger.info("Rejecting cat fact: unreliable source - {}", source);
            return EvaluationOutcome.success(); // Criteria met for rejection
        }
        
        // No rejection criteria met
        logger.debug("Cat fact does not meet rejection criteria");
        return EvaluationOutcome.fail("No rejection criteria met");
    }

    /**
     * Checks if a medical fact is verified.
     * In a real implementation, this would check against a database of verified medical facts.
     */
    private boolean isVerifiedMedicalFact(CatFact catFact) {
        // Simplified check - in reality, this would involve checking against
        // a database of verified medical facts or having a verification flag
        String source = catFact.getSource();
        
        // Consider facts from veterinary sources as verified
        return source != null && (
            source.toLowerCase().contains("veterinary") ||
            source.toLowerCase().contains("vet") ||
            source.toLowerCase().contains("medical") ||
            source.toLowerCase().contains("scientific")
        );
    }

    /**
     * Checks if the fact text contains medical content.
     */
    private boolean containsMedicalContent(String factText) {
        String lowerText = factText.toLowerCase();
        return MEDICAL_KEYWORDS.stream().anyMatch(lowerText::contains);
    }

    /**
     * Checks if the fact text contains controversial content.
     */
    private boolean containsControversialContent(String factText) {
        String lowerText = factText.toLowerCase();
        return CONTROVERSIAL_KEYWORDS.stream().anyMatch(lowerText::contains);
    }

    /**
     * Checks if the source is considered unreliable.
     */
    private boolean isUnreliableSource(String source) {
        if (source == null) {
            return true; // No source is unreliable
        }
        
        String lowerSource = source.toLowerCase();
        return UNRELIABLE_SOURCES.stream().anyMatch(lowerSource::contains);
    }
}

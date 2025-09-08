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
 * CatFactArchiveCriterion - Determines if cat fact should be archived
 * 
 * Purpose: Determine if cat fact should be archived
 * Input: CatFact entity
 * Output: Boolean (true if should archive, false otherwise)
 * 
 * Use Cases:
 * - USED → ARCHIVED transition
 */
@Component
public class CatFactArchiveCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CatFactArchiveCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking CatFact archive criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(CatFact.class, this::validateCatFactArchive)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for cat fact archive determination
     */
    private EvaluationOutcome validateCatFactArchive(CriterionSerializer.CriterionEntityEvaluationContext<CatFact> context) {
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

        // Check if usage count exceeds archive threshold
        if (catFact.getUsageCount() >= 10) {
            logger.debug("CatFact {} usage count {} >= 10, should archive",
                        catFact.getFactId(), catFact.getUsageCount());
            return EvaluationOutcome.success();
        }

        // Check if last used more than 365 days ago
        if (catFact.getLastUsedDate() != null) {
            LocalDateTime oneYearAgo = LocalDateTime.now().minusDays(365);
            if (catFact.getLastUsedDate().isBefore(oneYearAgo)) {
                logger.debug("CatFact {} was last used more than 365 days ago, should archive", catFact.getFactId());
                return EvaluationOutcome.success();
            }
        }

        // Check for outdated information (basic check)
        if (containsOutdatedInformation(catFact.getText())) {
            logger.debug("CatFact {} contains outdated information, should archive", catFact.getFactId());
            return EvaluationOutcome.success();
        }

        logger.debug("CatFact {} does not meet archive criteria", catFact.getFactId());
        return EvaluationOutcome.fail("Does not meet archive criteria", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }

    /**
     * Checks for outdated information in the cat fact text
     */
    private boolean containsOutdatedInformation(String text) {
        if (text == null) {
            return false;
        }

        String lowerText = text.toLowerCase();
        
        // List of potentially outdated terms/phrases
        String[] outdatedTerms = {
            "in 2010", "in 2011", "in 2012", "in 2013", "in 2014", "in 2015",
            "last year", "this year", "recently discovered", "new study shows",
            "scientists recently found", "latest research"
        };
        
        for (String term : outdatedTerms) {
            if (lowerText.contains(term)) {
                return true;
            }
        }
        
        return false;
    }
}

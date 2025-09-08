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
 * CatFactReuseCriterion - Determines if cat fact can be reused in another campaign
 * 
 * Purpose: Determine if cat fact can be reused in another campaign
 * Input: CatFact entity
 * Output: Boolean (true if can reuse, false otherwise)
 * 
 * Use Cases:
 * - USED → USED transition (reuse)
 */
@Component
public class CatFactReuseCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CatFactReuseCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking CatFact reuse criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(CatFact.class, this::validateCatFactReuse)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for cat fact reuse determination
     */
    private EvaluationOutcome validateCatFactReuse(CriterionSerializer.CriterionEntityEvaluationContext<CatFact> context) {
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

        // Check usage count limit
        if (catFact.getUsageCount() >= 5) {
            logger.debug("CatFact {} usage count {} >= 5, cannot reuse", 
                        catFact.getFactId(), catFact.getUsageCount());
            return EvaluationOutcome.fail("Usage count exceeded limit", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if last used within 30 days
        if (catFact.getLastUsedDate() != null) {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            if (catFact.getLastUsedDate().isAfter(thirtyDaysAgo)) {
                logger.debug("CatFact {} was last used within 30 days, cannot reuse", catFact.getFactId());
                return EvaluationOutcome.fail("Last used within 30 days", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // Check minimum text length for reuse
        if (catFact.getText().length() < 50) {
            logger.debug("CatFact {} text length {} < 50, too short for reuse", 
                        catFact.getFactId(), catFact.getText().length());
            return EvaluationOutcome.fail("Text too short for reuse", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("CatFact {} can be reused", catFact.getFactId());
        return EvaluationOutcome.success();
    }
}

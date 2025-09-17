package com.java_template.application.criterion;

import com.java_template.application.entity.hnitemsearch.version_1.HNItemSearch;
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
 * SearchSuccessfulCriterion - Determines if search execution was successful
 */
@Component
public class SearchSuccessfulCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SearchSuccessfulCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking SearchSuccessfulCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(HNItemSearch.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for determining search success
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItemSearch> context) {
        HNItemSearch entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("HNItemSearch is null");
            return EvaluationOutcome.fail("HNItemSearch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if resultCount is set and >= 0
        if (entity.getResultCount() == null) {
            logger.warn("HNItemSearch resultCount is null for search: {}", entity.getSearchId());
            return EvaluationOutcome.fail("Search result count is not set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (entity.getResultCount() < 0) {
            logger.warn("HNItemSearch resultCount is negative: {} for search: {}", 
                       entity.getResultCount(), entity.getSearchId());
            return EvaluationOutcome.fail("Search result count cannot be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if executionTimeMs is set and > 0
        if (entity.getExecutionTimeMs() == null) {
            logger.warn("HNItemSearch executionTimeMs is null for search: {}", entity.getSearchId());
            return EvaluationOutcome.fail("Search execution time is not set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (entity.getExecutionTimeMs() <= 0) {
            logger.warn("HNItemSearch executionTimeMs is not positive: {} for search: {}", 
                       entity.getExecutionTimeMs(), entity.getSearchId());
            return EvaluationOutcome.fail("Search execution time must be positive", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Additional validation: check if execution time is reasonable (not too high)
        if (entity.getExecutionTimeMs() > 60000) { // More than 1 minute
            logger.warn("HNItemSearch executionTimeMs is very high: {}ms for search: {}", 
                       entity.getExecutionTimeMs(), entity.getSearchId());
            // This is a warning, not a failure - search still succeeded but was slow
        }

        logger.debug("HNItemSearch {} passed success validation with {} results in {}ms", 
                    entity.getSearchId(), entity.getResultCount(), entity.getExecutionTimeMs());
        return EvaluationOutcome.success();
    }
}

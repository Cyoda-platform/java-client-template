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
 * ValidSearchQueryCriterion - Validates search query parameters
 */
@Component
public class ValidSearchQueryCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidSearchQueryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ValidSearchQueryCriterion for request: {}", request.getId());
        
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
     * Main validation logic for the HNItemSearch entity
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItemSearch> context) {
        HNItemSearch entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("HNItemSearch is null");
            return EvaluationOutcome.fail("HNItemSearch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if query is present and not empty
        if (entity.getQuery() == null || entity.getQuery().trim().isEmpty()) {
            logger.warn("HNItemSearch query is null or empty");
            return EvaluationOutcome.fail("Search query is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if searchType is present and not empty
        if (entity.getSearchType() == null || entity.getSearchType().trim().isEmpty()) {
            logger.warn("HNItemSearch searchType is null or empty");
            return EvaluationOutcome.fail("Search type is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate searchType is one of the allowed values
        String searchType = entity.getSearchType().toLowerCase();
        if (!searchType.equals("text") && !searchType.equals("author") && 
            !searchType.equals("type") && !searchType.equals("hierarchical")) {
            logger.warn("HNItemSearch has invalid searchType: {}", entity.getSearchType());
            return EvaluationOutcome.fail("Invalid search type: " + entity.getSearchType() + 
                ". Must be one of: text, author, type, hierarchical", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if searchId is present
        if (entity.getSearchId() == null || entity.getSearchId().trim().isEmpty()) {
            logger.warn("HNItemSearch searchId is null or empty");
            return EvaluationOutcome.fail("Search ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Use the entity's built-in validation
        if (!entity.isValid()) {
            logger.warn("HNItemSearch failed validation: searchId={}, query={}, searchType={}", 
                       entity.getSearchId(), entity.getQuery(), entity.getSearchType());
            return EvaluationOutcome.fail("HNItemSearch failed validation checks", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("HNItemSearch {} passed validation", entity.getSearchId());
        return EvaluationOutcome.success();
    }
}

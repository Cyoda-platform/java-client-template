package com.java_template.application.criterion;

import com.java_template.application.entity.searchquery.version_1.SearchQuery;
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
 * SearchQueryFailureCriterion - Determines if a search query execution failed
 */
@Component
public class SearchQueryFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SearchQueryFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking SearchQuery failure criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(SearchQuery.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<SearchQuery> context) {
        SearchQuery entity = context.entityWithMetadata().entity();

        if (entity == null) {
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Inverse of SearchQuerySuccessCriterion logic
        boolean isSuccess = true;

        // Check if execution completed without errors
        if (entity.getExecutionEndTime() == null) {
            isSuccess = false;
        }

        // Check if results were found (even empty results are successful)
        if (entity.getIntermediateResults() == null) {
            isSuccess = false;
        }

        // Check if no error occurred during execution
        if (entity.getErrorMessage() != null && !entity.getErrorMessage().trim().isEmpty()) {
            isSuccess = false;
        }

        if (!isSuccess) {
            return EvaluationOutcome.success("Search query execution failed");
        } else {
            return EvaluationOutcome.fail("Search query execution was successful", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}

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
 * SearchQuerySuccessCriterion - Determines if a search query execution was successful
 */
@Component
public class SearchQuerySuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SearchQuerySuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking SearchQuery success criteria for request: {}", request.getId());
        
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

        // Check if execution completed without errors
        if (entity.getExecutionEndTime() == null) {
            return EvaluationOutcome.fail("Execution not completed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if results were found (even empty results are successful)
        if (entity.getIntermediateResults() == null) {
            return EvaluationOutcome.fail("No intermediate results found", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if no error occurred during execution
        if (entity.getErrorMessage() != null && !entity.getErrorMessage().trim().isEmpty()) {
            return EvaluationOutcome.fail("Error occurred during execution: " + entity.getErrorMessage(), 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}

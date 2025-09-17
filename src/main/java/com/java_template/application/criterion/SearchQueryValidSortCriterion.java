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
 * SearchQueryValidSortCriterion - Validates that search query sort parameters are valid
 */
@Component
public class SearchQueryValidSortCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SearchQueryValidSortCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking SearchQuery valid sort criteria for request: {}", request.getId());
        
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

        String[] validSortBy = {"score", "time", "relevance"};
        String[] validSortOrder = {"asc", "desc"};

        // If sortBy is specified, it must be valid
        if (entity.getSortBy() != null) {
            boolean validSortByFound = false;
            for (String valid : validSortBy) {
                if (valid.equals(entity.getSortBy())) {
                    validSortByFound = true;
                    break;
                }
            }
            if (!validSortByFound) {
                return EvaluationOutcome.fail("Invalid sortBy parameter: " + entity.getSortBy(), 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // If sortOrder is specified, it must be valid
        if (entity.getSortOrder() != null) {
            boolean validSortOrderFound = false;
            for (String valid : validSortOrder) {
                if (valid.equals(entity.getSortOrder())) {
                    validSortOrderFound = true;
                    break;
                }
            }
            if (!validSortOrderFound) {
                return EvaluationOutcome.fail("Invalid sortOrder parameter: " + entity.getSortOrder(), 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}

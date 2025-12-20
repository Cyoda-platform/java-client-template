package com.example.application.criterion;

import com.example.application.entity.hacker_news_item.version_1.HackerNewsItem;
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
 * HackerNewsItemCriterion - Validates required fields for Hacker News items
 *
 * This criterion checks that both 'id' and 'type' fields are present
 * in the incoming Hacker News item. If validation passes, the item
 * transitions to VALIDATED state. If it fails, it transitions to REJECTED.
 */
@Component
public class HackerNewsItemCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HackerNewsItemCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking HackerNewsItem criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(HackerNewsItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that required fields (id and type) are present
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entityWithMetadata().entity();

        if (entity == null) {
            logger.warn("HackerNewsItem is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getId() == null) {
            logger.warn("HackerNewsItem missing required field: id");
            return EvaluationOutcome.fail("Missing required field: id", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getType() == null || entity.getType().isBlank()) {
            logger.warn("HackerNewsItem missing required field: type");
            return EvaluationOutcome.fail("Missing required field: type", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        logger.info("HackerNewsItem validation passed for id: {}, type: {}", entity.getId(), entity.getType());
        return EvaluationOutcome.success();
    }
}


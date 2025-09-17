package com.java_template.application.criterion;

import com.java_template.application.entity.hnitem.version_1.HNItem;
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
 * HNItemNeedsEnrichmentCriterion - Determines if an HN item needs enrichment
 * 
 * Evaluates whether an HN item requires enrichment based on its type and available data.
 */
@Component
public class HNItemNeedsEnrichmentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HNItemNeedsEnrichmentCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking HNItem enrichment criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(HNItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItem> context) {
        HNItem entity = context.entityWithMetadata().entity();

        if (entity == null) {
            logger.warn("HNItem is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Stories with URLs need domain extraction
        if ("story".equals(entity.getType()) && entity.getUrl() != null && !entity.getUrl().trim().isEmpty()) {
            logger.debug("HNItem {} needs enrichment: story with URL", entity.getId());
            return EvaluationOutcome.success("Story with URL needs enrichment");
        }

        // Items with text need text analysis
        if (entity.getText() != null && !entity.getText().trim().isEmpty()) {
            logger.debug("HNItem {} needs enrichment: has text content", entity.getId());
            return EvaluationOutcome.success("Item with text needs enrichment");
        }

        // Items with children need child count calculation
        if (entity.getKids() != null && !entity.getKids().isEmpty()) {
            logger.debug("HNItem {} needs enrichment: has children", entity.getId());
            return EvaluationOutcome.success("Item with children needs enrichment");
        }

        // Otherwise, no enrichment needed
        logger.debug("HNItem {} does not need enrichment", entity.getId());
        return EvaluationOutcome.fail("No enrichment needed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}

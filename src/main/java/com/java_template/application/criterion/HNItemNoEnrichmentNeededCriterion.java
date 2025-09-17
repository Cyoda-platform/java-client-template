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
 * HNItemNoEnrichmentNeededCriterion - Determines if an HN item can skip enrichment
 * 
 * Evaluates whether an HN item can go directly to indexing without enrichment.
 */
@Component
public class HNItemNoEnrichmentNeededCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HNItemNoEnrichmentNeededCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking HNItem no enrichment needed criteria for request: {}", request.getId());
        
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

        // Check if enrichment is needed (inverse of HNItemNeedsEnrichmentCriterion logic)
        boolean needsEnrichment = false;

        // Stories with URLs need domain extraction
        if ("story".equals(entity.getType()) && entity.getUrl() != null && !entity.getUrl().trim().isEmpty()) {
            needsEnrichment = true;
        }

        // Items with text need text analysis
        if (entity.getText() != null && !entity.getText().trim().isEmpty()) {
            needsEnrichment = true;
        }

        // Items with children need child count calculation
        if (entity.getKids() != null && !entity.getKids().isEmpty()) {
            needsEnrichment = true;
        }

        if (!needsEnrichment) {
            logger.debug("HNItem {} can skip enrichment", entity.getId());
            return EvaluationOutcome.success("No enrichment needed, can proceed to indexing");
        } else {
            logger.debug("HNItem {} needs enrichment", entity.getId());
            return EvaluationOutcome.fail("Enrichment is needed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}

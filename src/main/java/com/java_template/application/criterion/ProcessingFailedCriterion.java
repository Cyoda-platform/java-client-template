package com.java_template.application.criterion;

import com.java_template.application.entity.hnitem.version_1.HnItem;
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

import java.net.MalformedURLException;
import java.net.URL;

/**
 * ProcessingFailedCriterion - Check if processing has failed for an HN item
 * 
 * This criterion checks:
 * - URL validation for story items
 * - Required processing fields are missing after processing
 * 
 * Returns true if processing has failed, false if processing is successful.
 */
@Component
public class ProcessingFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProcessingFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ProcessingFailed criteria for HnItem request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(HnItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the entity
     * Returns success if processing has failed, fail if processing is successful
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HnItem> context) {
        HnItem entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("HnItem is null");
            return EvaluationOutcome.success("Entity is null - processing failed");
        }

        // Check if processing encountered issues with URL validation for stories
        if ("story".equals(entity.getType()) && entity.getUrl() != null && !entity.getUrl().trim().isEmpty()) {
            if (!isValidUrl(entity.getUrl())) {
                logger.warn("Invalid URL format for story item {}: {}", entity.getId(), entity.getUrl());
                return EvaluationOutcome.success("Invalid URL format - processing failed");
            }
        }

        // Check if required processing fields are missing after processing
        if (entity.getUpdatedAt() == null) {
            logger.warn("UpdatedAt field is missing after processing for item: {}", entity.getId());
            return EvaluationOutcome.success("UpdatedAt field missing after processing - processing failed");
        }

        // If we reach here, processing has not failed
        return EvaluationOutcome.fail("Processing has not failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }

    /**
     * Check if URL is valid
     */
    private boolean isValidUrl(String urlString) {
        try {
            new URL(urlString);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
}

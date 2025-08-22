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

@Component
public class ReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReadyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(HNItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItem> context) {
         HNItem entity = context.entity();
         if (entity == null) {
             logger.debug("ReadyCriterion: received null entity");
             return EvaluationOutcome.fail("HN item is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getId() == null) {
             return EvaluationOutcome.fail("HN item 'id' is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getBy() == null || entity.getBy().isBlank()) {
             return EvaluationOutcome.fail("HN item 'by' (author) is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getTime() == null) {
             return EvaluationOutcome.fail("HN item 'time' (unix timestamp) is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getType() == null || entity.getType().isBlank()) {
             return EvaluationOutcome.fail("HN item 'type' is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rules for story items
         if ("story".equalsIgnoreCase(entity.getType())) {
             if (entity.getTitle() == null || entity.getTitle().isBlank()) {
                 return EvaluationOutcome.fail("HN story must have a non-blank title", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             // Data quality: story should contain either a URL or text
             boolean hasUrl = entity.getUrl() != null && !entity.getUrl().isBlank();
             boolean hasText = entity.getText() != null && !entity.getText().isBlank();
             if (!hasUrl && !hasText) {
                 return EvaluationOutcome.fail("HN story should contain either 'url' or 'text'", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // For other types (e.g., comment, job) ensure minimal content where applicable
         if ("comment".equalsIgnoreCase(entity.getType())) {
             if (entity.getText() == null || entity.getText().isBlank()) {
                 return EvaluationOutcome.fail("HN comment must have non-blank 'text'", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // If rawJson is missing, treat as a warning (kept as reason attachment by serializer)
         if (entity.getRawJson() == null || entity.getRawJson().isBlank()) {
             logger.debug("ReadyCriterion: rawJson missing for HN item id={}", entity.getId());
             return EvaluationOutcome.fail("raw_json missing - original payload not preserved", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}
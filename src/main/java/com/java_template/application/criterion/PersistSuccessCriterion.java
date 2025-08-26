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

import java.time.Instant;

@Component
public class PersistSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PersistSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(HNItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItem> context) {
         HNItem entity = context.entity();
         if (entity == null) {
             logger.warn("PersistSuccessCriterion: entity is null");
             return EvaluationOutcome.fail("Entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // id must be present and positive
         if (entity.getId() == null || entity.getId() <= 0) {
             return EvaluationOutcome.fail("id is required and must be a positive number", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // type must be present
         if (entity.getType() == null || entity.getType().isBlank()) {
             return EvaluationOutcome.fail("type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // originalJson must be present and non-blank
         if (entity.getOriginalJson() == null || entity.getOriginalJson().isBlank()) {
             return EvaluationOutcome.fail("originalJson must be present", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // importTimestamp must be present and a valid ISO-8601 instant
         if (entity.getImportTimestamp() == null || entity.getImportTimestamp().isBlank()) {
             return EvaluationOutcome.fail("importTimestamp must be present", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         try {
             Instant.parse(entity.getImportTimestamp());
         } catch (Exception e) {
             return EvaluationOutcome.fail("importTimestamp must be ISO-8601 UTC", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // status must indicate stored state for a persist success
         if (entity.getStatus() == null || !entity.getStatus().equalsIgnoreCase("STORED")) {
             return EvaluationOutcome.fail("status must be STORED for a successful persist", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}
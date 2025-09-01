package com.java_template.application.criterion;

import com.java_template.application.entity.catfact.version_1.CatFact;
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
public class ValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(CatFact.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<CatFact> context) {
         CatFact entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("CatFact entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If explicit validation status marked INVALID -> fail
         if (entity.getValidationStatus() != null && entity.getValidationStatus().equalsIgnoreCase("INVALID")) {
             return EvaluationOutcome.fail("CatFact validationStatus is INVALID", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic content validation consistent with ValidateCatFactProcessor rules
         String text = entity.getText();
         if (text == null || text.isBlank()) {
             return EvaluationOutcome.fail("CatFact text must be present and non-blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (text.length() > 1000) {
             return EvaluationOutcome.fail("CatFact text exceeds maximum length of 1000 characters", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Ensure required metadata present
         if (entity.getSource() == null || entity.getSource().isBlank()) {
             return EvaluationOutcome.fail("CatFact source is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getFetchedAt() == null || entity.getFetchedAt().isBlank()) {
             return EvaluationOutcome.fail("CatFact fetchedAt is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Numeric fields sanity checks
         if (entity.getSendCount() == null || entity.getSendCount() < 0) {
             return EvaluationOutcome.fail("CatFact sendCount must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getEngagementScore() == null) {
             return EvaluationOutcome.fail("CatFact engagementScore must be present", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}
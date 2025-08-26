package com.java_template.application.criterion;

import com.java_template.application.entity.laureate.version_1.Laureate;
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
public class ValidationSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();

         if (entity == null) {
             logger.debug("Laureate entity is null in ValidationSuccessCriterion");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // technicalId must be present
         if (entity.getTechnicalId() == null || entity.getTechnicalId().isBlank()) {
             return EvaluationOutcome.fail("technicalId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // id must be present
         if (entity.getId() == null) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // must have either full name or firstname+surname
         boolean hasFullName = entity.getName() != null && !entity.getName().isBlank();
         boolean hasParts = entity.getFirstname() != null && !entity.getFirstname().isBlank()
                 && entity.getSurname() != null && !entity.getSurname().isBlank();
         if (!hasFullName && !hasParts) {
             return EvaluationOutcome.fail("Either full name or both firstname and surname must be provided",
                     StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // category and year are required
         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
             return EvaluationOutcome.fail("category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getYear() == null || entity.getYear().isBlank()) {
             return EvaluationOutcome.fail("year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If an explicit validationStatus exists and marks invalid, fail
         if (entity.getValidationStatus() != null && entity.getValidationStatus().equalsIgnoreCase("INVALID")) {
             return EvaluationOutcome.fail("validationStatus is INVALID", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // All checks passed -> validation success
         return EvaluationOutcome.success();
    }
}
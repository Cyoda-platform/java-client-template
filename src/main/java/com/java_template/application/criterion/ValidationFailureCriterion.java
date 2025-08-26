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
public class ValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailureCriterion(SerializerFactory serializerFactory) {
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
             logger.debug("ValidationFailureCriterion: entity is null");
             return EvaluationOutcome.fail("Laureate entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getId() == null) {
             return EvaluationOutcome.fail("Laureate.id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getYear() == null || entity.getYear().isBlank()) {
             return EvaluationOutcome.fail("Laureate.year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
             return EvaluationOutcome.fail("Laureate.category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         boolean hasFullName = entity.getName() != null && !entity.getName().isBlank();
         boolean hasParts = entity.getFirstname() != null && !entity.getFirstname().isBlank()
                 && entity.getSurname() != null && !entity.getSurname().isBlank();
         if (!hasFullName && !hasParts) {
             return EvaluationOutcome.fail("Laureate must have either name or firstname and surname", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}
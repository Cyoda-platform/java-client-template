package com.java_template.application.criterion;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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
public class RequestValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public RequestValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(AdoptionRequest.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionRequest> context) {
         AdoptionRequest entity = context.entity();

         // Basic required fields
         if (entity.getPetId() == null || entity.getPetId().isBlank()) {
             return EvaluationOutcome.fail("petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRequesterName() == null || entity.getRequesterName().isBlank()) {
             return EvaluationOutcome.fail("requesterName is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // At least one contact method required
         boolean hasEmail = entity.getContactEmail() != null && !entity.getContactEmail().isBlank();
         boolean hasPhone = entity.getContactPhone() != null && !entity.getContactPhone().isBlank();
         if (!hasEmail && !hasPhone) {
             return EvaluationOutcome.fail("At least one contact method (contactEmail or contactPhone) is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getMotivation() == null || entity.getMotivation().isBlank()) {
             return EvaluationOutcome.fail("motivation is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSubmittedAt() == null || entity.getSubmittedAt().isBlank()) {
             return EvaluationOutcome.fail("submittedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality checks
         if (entity.getMotivation() != null && entity.getMotivation().length() > 0 && entity.getMotivation().length() < 10) {
             return EvaluationOutcome.fail("motivation is too short", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (hasEmail && !entity.getContactEmail().contains("@")) {
             return EvaluationOutcome.fail("contactEmail appears invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If all checks pass
         return EvaluationOutcome.success();
    }
}
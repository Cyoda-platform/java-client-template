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

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Component
public class ScreeningCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ScreeningCompleteCriterion(SerializerFactory serializerFactory) {
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

         // Basic required fields validation (use only existing properties)
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("AdoptionRequest.id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUserId() == null || entity.getUserId().isBlank()) {
             return EvaluationOutcome.fail("AdoptionRequest.userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPetId() == null || entity.getPetId().isBlank()) {
             return EvaluationOutcome.fail("AdoptionRequest.petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRequestedDate() == null || entity.getRequestedDate().isBlank()) {
             return EvaluationOutcome.fail("AdoptionRequest.requestedDate is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate requestedDate is a valid ISO-8601 date-time
         try {
             OffsetDateTime.parse(entity.getRequestedDate());
         } catch (DateTimeParseException ex) {
             logger.debug("Invalid requestedDate for AdoptionRequest {}: {}", entity.getId(), entity.getRequestedDate());
             return EvaluationOutcome.fail("requestedDate is not a valid ISO-8601 date-time", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: the request must be in 'screening' status to be considered screening-complete
         String status = entity.getStatus();
         if (status == null || !status.equalsIgnoreCase("screening")) {
             return EvaluationOutcome.fail("AdoptionRequest must be in 'screening' status to complete screening", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All checks passed -> screening can be considered complete
         return EvaluationOutcome.success();
    }
}
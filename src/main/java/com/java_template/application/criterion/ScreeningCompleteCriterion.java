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

import java.time.Instant;
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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(AdoptionRequest.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionRequest> context) {
         AdoptionRequest entity = context.entity();
         if (entity == null) {
             logger.warn("AdoptionRequest entity is null in ScreeningCompleteCriterion");
             return EvaluationOutcome.fail("AdoptionRequest entity missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required identifiers
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("AdoptionRequest.id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUserId() == null || entity.getUserId().isBlank()) {
             return EvaluationOutcome.fail("AdoptionRequest.userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPetId() == null || entity.getPetId().isBlank()) {
             return EvaluationOutcome.fail("AdoptionRequest.petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // requestedDate must be present and a valid ISO-8601 date-time
         if (entity.getRequestedDate() == null || entity.getRequestedDate().isBlank()) {
             return EvaluationOutcome.fail("AdoptionRequest.requestedDate is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         try {
             Instant.parse(entity.getRequestedDate());
         } catch (DateTimeParseException ex) {
             return EvaluationOutcome.fail("AdoptionRequest.requestedDate must be ISO-8601 date-time", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: Only requests in 'screening' state are eligible to complete screening
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("AdoptionRequest.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"screening".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("AdoptionRequest must be in 'screening' state to complete screening", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All checks passed: screening considered complete for this criterion
         return EvaluationOutcome.success();
    }
}
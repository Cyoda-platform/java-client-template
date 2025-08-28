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
import java.util.Set;

@Component
public class AdoptionRequestValidationPassedFunction implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AdoptionRequestValidationPassedFunction(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionRequest> context) {
         AdoptionRequest entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("AdoptionRequest entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields checks
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPetId() == null || entity.getPetId().isBlank()) {
             return EvaluationOutcome.fail("petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRequesterId() == null || entity.getRequesterId().isBlank()) {
             return EvaluationOutcome.fail("requesterId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSubmittedAt() == null || entity.getSubmittedAt().isBlank()) {
             return EvaluationOutcome.fail("submittedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate status is one of the expected workflow values
         Set<String> allowedStatuses = Set.of(
             "submitted",
             "under_review",
             "approved",
             "rejected",
             "cancelled",
             "completed"
         );
         String status = entity.getStatus().trim().toLowerCase();
         if (!allowedStatuses.contains(status)) {
             return EvaluationOutcome.fail("status value is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Validate timestamp formats
         try {
             Instant.parse(entity.getSubmittedAt());
         } catch (Exception e) {
             logger.debug("submittedAt parse failed for id {}: {}", entity.getId(), e.getMessage());
             return EvaluationOutcome.fail("submittedAt is not a valid ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (entity.getProcessedAt() != null && !entity.getProcessedAt().isBlank()) {
             try {
                 Instant.parse(entity.getProcessedAt());
             } catch (Exception e) {
                 logger.debug("processedAt parse failed for id {}: {}", entity.getId(), e.getMessage());
                 return EvaluationOutcome.fail("processedAt is not a valid ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rules around processing fields and status
         if (status.equals("approved") || status.equals("rejected") || status.equals("completed")) {
             // processedBy and processedAt should be present for finalized states
             if (entity.getProcessedBy() == null || entity.getProcessedBy().isBlank()) {
                 return EvaluationOutcome.fail("processedBy must be provided when request is finalized", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             if (entity.getProcessedAt() == null || entity.getProcessedAt().isBlank()) {
                 return EvaluationOutcome.fail("processedAt must be provided when request is finalized", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         } else {
             // For in-flight states, processedBy/processedAt should normally be empty
             if ((entity.getProcessedBy() != null && !entity.getProcessedBy().isBlank())
                 || (entity.getProcessedAt() != null && !entity.getProcessedAt().isBlank())) {
                 return EvaluationOutcome.fail("processedBy/processedAt should be empty for non-finalized requests", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Passed all checks
         return EvaluationOutcome.success();
    }
}
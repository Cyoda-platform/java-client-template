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

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Set;

@Component
public class AdoptionRequestValidationFailedFunction implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AdoptionRequestValidationFailedFunction(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionRequest> context) {
         AdoptionRequest entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("AdoptionRequest entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identifiers
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPetId() == null || entity.getPetId().isBlank()) {
             return EvaluationOutcome.fail("petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRequesterId() == null || entity.getRequesterId().isBlank()) {
             return EvaluationOutcome.fail("requesterId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status validations
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus().trim().toLowerCase();
         Set<String> allowedStatuses = Set.of("submitted", "approved", "rejected", "cancelled", "under_review", "completed");
         if (!allowedStatuses.contains(status)) {
             return EvaluationOutcome.fail("status must be one of " + allowedStatuses, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // submittedAt must be a valid ISO-8601 timestamp
         if (entity.getSubmittedAt() == null || entity.getSubmittedAt().isBlank()) {
             return EvaluationOutcome.fail("submittedAt is required and must be an ISO-8601 timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         try {
             Instant.parse(entity.getSubmittedAt());
         } catch (DateTimeException dte) {
             return EvaluationOutcome.fail("submittedAt must be a valid ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If processedAt provided ensure valid format
         if (entity.getProcessedAt() != null && !entity.getProcessedAt().isBlank()) {
             try {
                 Instant.parse(entity.getProcessedAt());
             } catch (DateTimeException dte) {
                 return EvaluationOutcome.fail("processedAt must be a valid ISO-8601 timestamp when present", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rules:
         // - If request is approved or rejected it must have processedAt and processedBy
         if ("approved".equals(status) || "rejected".equals(status)) {
             if (entity.getProcessedAt() == null || entity.getProcessedAt().isBlank()) {
                 return EvaluationOutcome.fail("processedAt is required when status is " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             if (entity.getProcessedBy() == null || entity.getProcessedBy().isBlank()) {
                 return EvaluationOutcome.fail("processedBy is required when status is " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // - If request is still submitted or under_review it should not have processing metadata
         if ("submitted".equals(status) || "under_review".equals(status)) {
             if ((entity.getProcessedAt() != null && !entity.getProcessedAt().isBlank()) ||
                 (entity.getProcessedBy() != null && !entity.getProcessedBy().isBlank())) {
                 return EvaluationOutcome.fail("processedAt/processedBy must be empty while request is in '" + status + "' state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }
}
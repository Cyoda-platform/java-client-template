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

@Component
public class AdoptionApprovedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AdoptionApprovedCriterion(SerializerFactory serializerFactory) {
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
         if (entity == null) {
             return EvaluationOutcome.fail("AdoptionRequest entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         // If not approved, criterion is not applicable -> success (no failure)
         if (status == null || !status.equalsIgnoreCase("approved")) {
             return EvaluationOutcome.success();
         }

         // For approved requests enforce presence of reviewerId and decisionAt and submittedAt
         String reviewerId = entity.getReviewerId();
         if (reviewerId == null || reviewerId.isBlank()) {
             return EvaluationOutcome.fail("Approved adoption request must have a reviewerId", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         String decisionAt = entity.getDecisionAt();
         if (decisionAt == null || decisionAt.isBlank()) {
             return EvaluationOutcome.fail("Approved adoption request must have decisionAt timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String submittedAt = entity.getSubmittedAt();
         if (submittedAt == null || submittedAt.isBlank()) {
             return EvaluationOutcome.fail("Adoption request submittedAt timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate timestamp formats and ordering (decision must not be before submission)
         try {
             Instant submitted = Instant.parse(submittedAt);
             Instant decision = Instant.parse(decisionAt);
             if (decision.isBefore(submitted)) {
                 return EvaluationOutcome.fail("decisionAt is before submittedAt", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         } catch (DateTimeException ex) {
             logger.debug("Timestamp parse error for AdoptionRequest {}: {}", entity.getRequestId(), ex.getMessage());
             return EvaluationOutcome.fail("Invalid ISO-8601 timestamp in submittedAt or decisionAt", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}
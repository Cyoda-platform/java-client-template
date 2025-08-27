package com.java_template.application.criterion;

import com.java_template.application.entity.getuserresult.version_1.GetUserResult;
import com.java_template.application.entity.user.version_1.User;
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
public class ResultAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ResultAvailableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(GetUserResult.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Use exact criterion name match (case-sensitive) as required by critical requirements
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<GetUserResult> context) {
         GetUserResult entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("Result entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getJobReference() == null || entity.getJobReference().isBlank()) {
             return EvaluationOutcome.fail("jobReference is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getRetrievedAt() == null || entity.getRetrievedAt().isBlank()) {
             return EvaluationOutcome.fail("retrievedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus().trim().toUpperCase();

         switch (status) {
             case "SUCCESS":
                 User user = entity.getUser();
                 if (user == null) {
                     return EvaluationOutcome.fail("User payload is required for SUCCESS results", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 // Validate essential user fields using available getters
                 if (user.getId() == null || user.getId() <= 0) {
                     return EvaluationOutcome.fail("User.id must be a positive integer", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 if (user.getEmail() == null || user.getEmail().isBlank()) {
                     return EvaluationOutcome.fail("User.email is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 if (user.getFirstName() == null || user.getFirstName().isBlank()) {
                     return EvaluationOutcome.fail("User.firstName is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 if (user.getLastName() == null || user.getLastName().isBlank()) {
                     return EvaluationOutcome.fail("User.lastName is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 if (user.getSource() == null || user.getSource().isBlank()) {
                     return EvaluationOutcome.fail("User.source is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 if (user.getRetrievedAt() == null || user.getRetrievedAt().isBlank()) {
                     return EvaluationOutcome.fail("User.retrievedAt is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 break;

             case "NOT_FOUND":
             case "INVALID_INPUT":
             case "ERROR":
                 if (entity.getErrorMessage() == null || entity.getErrorMessage().isBlank()) {
                     return EvaluationOutcome.fail("errorMessage is required for non-success statuses", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
                 break;

             default:
                 return EvaluationOutcome.fail("Unknown status value: " + entity.getStatus(), StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}
package com.java_template.application.criterion;

import com.java_template.application.entity.adoptionorder.version_1.AdoptionOrder;
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
public class NoFollowupCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NoFollowupCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(AdoptionOrder.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionOrder> context) {
         AdoptionOrder entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("AdoptionOrder entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         String approvedDate = entity.getApprovedDate();
         String completedDate = entity.getCompletedDate();

         // Ensure required dates are present for terminal statuses
         if ("completed".equalsIgnoreCase(status)) {
             if (completedDate == null || completedDate.isBlank()) {
                 return EvaluationOutcome.fail("completedDate is required when status is 'completed'", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         if ("approved".equalsIgnoreCase(status)) {
             if (approvedDate == null || approvedDate.isBlank()) {
                 return EvaluationOutcome.fail("approvedDate is required when status is 'approved'", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Data quality checks: dates should not be present for statuses that don't match
         if (!"approved".equalsIgnoreCase(status) && approvedDate != null && !approvedDate.isBlank()) {
             return EvaluationOutcome.fail("approvedDate present but order status is '" + status + "'", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (!"completed".equalsIgnoreCase(status) && completedDate != null && !completedDate.isBlank()) {
             return EvaluationOutcome.fail("completedDate present but order status is '" + status + "'", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: completed orders should have been approved previously (approvedDate present or status progression)
         if ("completed".equalsIgnoreCase(status)) {
             if (approvedDate == null || approvedDate.isBlank()) {
                 return EvaluationOutcome.fail("completed order must have an approvedDate indicating it passed approval", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

        return EvaluationOutcome.success();
    }
}
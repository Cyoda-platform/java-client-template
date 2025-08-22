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
public class HoldCancelledCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HoldCancelledCriterion(SerializerFactory serializerFactory) {
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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionOrder> context) {
         AdoptionOrder entity = context.entity();

         // Basic presence check for status
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("AdoptionOrder.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus().trim();

         // Canonical allowed statuses per functional requirements
         boolean allowed =
             "requested".equals(status) ||
             "under_review".equals(status) ||
             "approved".equals(status) ||
             "declined".equals(status) ||
             "cancelled".equals(status) ||
             "completed".equals(status);

         if (!allowed) {
             return EvaluationOutcome.fail("Unknown AdoptionOrder.status: " + status, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rules specific to cancelled orders
         if ("cancelled".equals(status)) {
             // A cancelled order must not be marked as completed.
             if (entity.getCompletedDate() != null && !entity.getCompletedDate().isBlank()) {
                 return EvaluationOutcome.fail("Cancelled order must not have a completedDate", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             // cancelled orders may be approved previously; approvedDate is allowed.
             // Ensure references exist (these are required by entity.isValid, but we double-check defensively)
             if (entity.getPetId() == null || entity.getPetId().isBlank()) {
                 return EvaluationOutcome.fail("Cancelled order must reference a petId", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (entity.getUserId() == null || entity.getUserId().isBlank()) {
                 return EvaluationOutcome.fail("Cancelled order must reference a userId", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

        return EvaluationOutcome.success();
    }
}
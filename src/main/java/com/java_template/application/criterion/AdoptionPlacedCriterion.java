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
public class AdoptionPlacedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AdoptionPlacedCriterion(SerializerFactory serializerFactory) {
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
             return EvaluationOutcome.fail("AdoptionOrder entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required identifiers and references
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPetId() == null || entity.getPetId().isBlank()) {
             return EvaluationOutcome.fail("petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUserId() == null || entity.getUserId().isBlank()) {
             return EvaluationOutcome.fail("userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Dates and status
         if (entity.getRequestedDate() == null || entity.getRequestedDate().isBlank()) {
             return EvaluationOutcome.fail("requestedDate is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // For a freshly placed adoption order the canonical status should be "requested"
         if (!"requested".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("status must be 'requested' for a newly placed adoption order", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Pickup method validation: allowed values per functional requirements
         if (entity.getPickupMethod() == null || entity.getPickupMethod().isBlank()) {
             return EvaluationOutcome.fail("pickupMethod is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String pm = entity.getPickupMethod();
         if (!"inStore".equals(pm) && !"homeDelivery".equals(pm) && !"inStore".equalsIgnoreCase(pm) && !"homeDelivery".equalsIgnoreCase(pm)) {
             return EvaluationOutcome.fail("pickupMethod must be 'inStore' or 'homeDelivery'", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rules: approvedDate/completedDate should not be set on newly placed orders
         if (entity.getApprovedDate() != null && !entity.getApprovedDate().isBlank()) {
             return EvaluationOutcome.fail("approvedDate must be null for a newly placed adoption order", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         if (entity.getCompletedDate() != null && !entity.getCompletedDate().isBlank()) {
             return EvaluationOutcome.fail("completedDate must be null for a newly placed adoption order", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Notes may be present but should not be blank-only if provided (data quality)
         if (entity.getNotes() != null && entity.getNotes().isBlank()) {
             return EvaluationOutcome.fail("notes, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}
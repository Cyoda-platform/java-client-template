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
public class OnAdoptionHoldCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OnAdoptionHoldCriterion(SerializerFactory serializerFactory) {
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

         // Basic presence checks (validation failures)
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("AdoptionOrder.id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPetId() == null || entity.getPetId().isBlank()) {
             return EvaluationOutcome.fail("AdoptionOrder.petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUserId() == null || entity.getUserId().isBlank()) {
             return EvaluationOutcome.fail("AdoptionOrder.userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRequestedDate() == null || entity.getRequestedDate().isBlank()) {
             return EvaluationOutcome.fail("AdoptionOrder.requestedDate is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPickupMethod() == null || entity.getPickupMethod().isBlank()) {
             return EvaluationOutcome.fail("AdoptionOrder.pickupMethod is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rules for adoption hold:
         // - Order must be in "approved" status to trigger a hold
         // - approvedDate must be present when status is "approved"
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("AdoptionOrder.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (!"approved".equals(status)) {
             return EvaluationOutcome.fail("Adoption hold requires order status to be 'approved'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if (entity.getApprovedDate() == null || entity.getApprovedDate().isBlank()) {
             return EvaluationOutcome.fail("AdoptionOrder.approvedDate must be set when status is 'approved'", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate pickupMethod is one of the supported options
         String pm = entity.getPickupMethod();
         if (!"inStore".equals(pm) && !"homeDelivery".equals(pm)) {
             return EvaluationOutcome.fail("AdoptionOrder.pickupMethod must be 'inStore' or 'homeDelivery'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}
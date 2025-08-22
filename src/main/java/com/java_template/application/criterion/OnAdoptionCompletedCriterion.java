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
public class OnAdoptionCompletedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OnAdoptionCompletedCriterion(SerializerFactory serializerFactory) {
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
         AdoptionOrder order = context.entity();

         if (order == null) {
             logger.warn("OnAdoptionCompletedCriterion invoked with null entity");
             return EvaluationOutcome.fail("AdoptionOrder entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required references
         if (order.getPetId() == null || order.getPetId().isBlank()) {
             return EvaluationOutcome.fail("petId is required for adoption completion", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (order.getUserId() == null || order.getUserId().isBlank()) {
             return EvaluationOutcome.fail("userId is required for adoption completion", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status must indicate completion
         if (order.getStatus() == null || !order.getStatus().equalsIgnoreCase("completed")) {
             return EvaluationOutcome.fail("Adoption order is not in 'completed' status", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // completedDate must be present for a completed order
         if (order.getCompletedDate() == null || order.getCompletedDate().isBlank()) {
             return EvaluationOutcome.fail("completedDate is required for completed adoption orders", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // approvedDate is preferred for auditability but may be nullable in some flows;
         // do not fail, but attach a data-quality warning if missing.
         if (order.getApprovedDate() == null || order.getApprovedDate().isBlank()) {
             // Return success but allow the serializer to attach a warning via ReasonAttachmentStrategy
             logger.info("AdoptionOrder {} completed without approvedDate", order.getId());
         }

        return EvaluationOutcome.success();
    }
}
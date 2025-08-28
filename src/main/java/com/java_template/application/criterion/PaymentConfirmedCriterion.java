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

@Component
public class PaymentConfirmedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentConfirmedCriterion(SerializerFactory serializerFactory) {
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

         // Basic required fields (AdoptionRequest.isValid enforces most, but be explicit for clear reasons)
         if (entity.getRequestId() == null || entity.getRequestId().isBlank()) {
             return EvaluationOutcome.fail("requestId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPetId() == null || entity.getPetId().isBlank()) {
             return EvaluationOutcome.fail("petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUserId() == null || entity.getUserId().isBlank()) {
             return EvaluationOutcome.fail("userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPaymentStatus() == null || entity.getPaymentStatus().isBlank()) {
             return EvaluationOutcome.fail("paymentStatus is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality: adoptionFee must be present and non-negative
         if (entity.getAdoptionFee() == null) {
             return EvaluationOutcome.fail("adoptionFee is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getAdoptionFee() < 0) {
             return EvaluationOutcome.fail("adoptionFee must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: Only evaluate payment confirmation when request is in PAYMENT_PENDING state
         if (!"PAYMENT_PENDING".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("AdoptionRequest must be in PAYMENT_PENDING status to confirm payment", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Business rule: paymentStatus must indicate payment completed
         if (!"PAID".equalsIgnoreCase(entity.getPaymentStatus())) {
             return EvaluationOutcome.fail("Payment not confirmed (paymentStatus != PAID)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}
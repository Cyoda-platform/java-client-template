package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
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
import java.time.format.DateTimeParseException;

@Component
public class OrderAdoptedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderAdoptedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match the exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
         Order order = context.entity();
         if (order == null) {
             return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Validate timestamp ordering: expiresAt must be after createdAt
         String createdAt = order.getCreatedAt();
         String expiresAt = order.getExpiresAt();
         if (createdAt == null || createdAt.isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (expiresAt == null || expiresAt.isBlank()) {
             return EvaluationOutcome.fail("expiresAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         try {
             Instant created = Instant.parse(createdAt);
             Instant expires = Instant.parse(expiresAt);
             if (expires.isBefore(created) || expires.equals(created)) {
                 return EvaluationOutcome.fail("expiresAt must be after createdAt", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } catch (DateTimeParseException dte) {
             logger.debug("Invalid timestamp format on order {}: {}", order.getId(), dte.getMessage());
             return EvaluationOutcome.fail("Invalid timestamp format for createdAt or expiresAt", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule specific to adoption orders:
         // If the order is an adoption, it must be in 'completed' status to be considered adopted.
         String type = order.getType();
         String status = order.getStatus();
         if (type != null && "adopt".equalsIgnoreCase(type)) {
             if (status == null || !"completed".equalsIgnoreCase(status)) {
                 return EvaluationOutcome.fail("Adopt orders must be in 'completed' status", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // For any completed order, ensure essential references exist (petId and userId)
         if (status != null && "completed".equalsIgnoreCase(status)) {
             if (order.getPetId() == null || order.getPetId().isBlank()) {
                 return EvaluationOutcome.fail("Completed orders must reference a petId", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (order.getUserId() == null || order.getUserId().isBlank()) {
                 return EvaluationOutcome.fail("Completed orders must reference a userId", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

        return EvaluationOutcome.success();
    }
}
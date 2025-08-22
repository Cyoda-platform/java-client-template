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

@Component
public class InventoryReservedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public InventoryReservedCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
         Order entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic entity validity (structure & required fields)
         if (!entity.isValid()) {
             return EvaluationOutcome.fail("Order failed basic entity validation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Ensure itemsSnapshot sums to totalAmount (data quality check)
         double computedTotal = 0.0;
         if (entity.getItemsSnapshot() == null || entity.getItemsSnapshot().isEmpty()) {
             return EvaluationOutcome.fail("Order itemsSnapshot is missing or empty", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         for (Order.Item it : entity.getItemsSnapshot()) {
             if (it == null || !it.isValid()) {
                 return EvaluationOutcome.fail("Order contains invalid item in itemsSnapshot", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             computedTotal += it.getUnitPrice() * it.getQuantity();
         }
         Double totalAmount = entity.getTotalAmount();
         if (totalAmount == null) {
             return EvaluationOutcome.fail("Order totalAmount is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (Math.abs(computedTotal - totalAmount) > 0.01) {
             return EvaluationOutcome.fail("Order totalAmount does not match sum of itemsSnapshot", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: inventory reservation is expected to be performed when an order is in WAITING_TO_FULFILL or later.
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Order status is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // We cannot access Product inventory here; the contract is that when an Order reaches WAITING_TO_FULFILL (or beyond)
         // CreateOrderProcessor should have reserved inventory. Fail if order is not yet in a state where reservation should have happened.
         if ("WAITING_TO_FULFILL".equals(status) || "PICKING".equals(status) || "SENT".equals(status)) {
             return EvaluationOutcome.success();
         } else {
             return EvaluationOutcome.fail("Order is not in a state where inventory reservation is expected", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
    }
}
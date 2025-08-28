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
public class PickingCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PickingCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Business logic implemented in validateEntity method.
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name
        return "PickingCompleteCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
         Order order = context.entity();

         if (order == null) {
             logger.warn("PickingCompleteCriterion invoked with null order entity");
             return EvaluationOutcome.fail("Order entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Criterion expected to run when order is in PICKING state
         String status = order.getStatus();
         if (status == null || !status.equalsIgnoreCase("PICKING")) {
             logger.debug("Order {} not in PICKING state (status={})", order.getOrderNumber(), status);
             return EvaluationOutcome.fail("Order not in PICKING state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if (order.getItems() == null || order.getItems().isEmpty()) {
             logger.warn("Order {} has no items", order.getOrderNumber());
             return EvaluationOutcome.fail("Order has no items", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         for (Order.OrderItem item : order.getItems()) {
             if (item == null) {
                 return EvaluationOutcome.fail("Order contains null item", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             String productId = item.getProductId() != null ? item.getProductId() : "<unknown>";
             Integer qtyOrdered = item.getQtyOrdered();
             Integer qtyFulfilled = item.getQtyFulfilled();

             if (qtyOrdered == null) {
                 logger.warn("Order {} item {} missing qtyOrdered", order.getOrderNumber(), productId);
                 return EvaluationOutcome.fail("Item " + productId + " missing qtyOrdered", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (qtyFulfilled == null) {
                 logger.debug("Order {} item {} has no qtyFulfilled yet", order.getOrderNumber(), productId);
                 return EvaluationOutcome.fail("Item " + productId + " has no qtyFulfilled", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             if (qtyFulfilled < qtyOrdered) {
                 logger.debug("Order {} item {} not fully picked: {}/{}", order.getOrderNumber(), productId, qtyFulfilled, qtyOrdered);
                 return EvaluationOutcome.fail(
                     "Item " + productId + " not fully picked (" + qtyFulfilled + " of " + qtyOrdered + ")",
                     StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                 );
             }
             if (qtyFulfilled > qtyOrdered) {
                 logger.warn("Order {} item {} qtyFulfilled ({}) exceeds qtyOrdered ({})", order.getOrderNumber(), productId, qtyFulfilled, qtyOrdered);
                 return EvaluationOutcome.fail(
                     "Item " + productId + " qtyFulfilled exceeds qtyOrdered (" + qtyFulfilled + " > " + qtyOrdered + ")",
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE
                 );
             }
         }

         logger.debug("Order {} passed PickingCompleteCriterion checks", order.getOrderNumber());
        return EvaluationOutcome.success();
    }
}
package com.java_template.application.criterion;

import com.java_template.application.entity.Order;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public OrderValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("OrderValidationCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "OrderValidationCriterion".equals(modelSpec.operationName()) &&
               "order".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Order order) {
        if (order.getOrderId() == null || order.getOrderId().isBlank()) {
            return EvaluationOutcome.fail("Order ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (order.getCustomerName() == null || order.getCustomerName().isBlank()) {
            return EvaluationOutcome.fail("Customer name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (order.getProductCode() == null || order.getProductCode().isBlank()) {
            return EvaluationOutcome.fail("Product code is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            return EvaluationOutcome.fail("Quantity must be greater than zero", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (order.getOrderDate() == null || order.getOrderDate().isBlank()) {
            return EvaluationOutcome.fail("Order date is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (order.getStatus() == null || order.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Order status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Business rule: status must be NEW for validation
        if (!"NEW".equalsIgnoreCase(order.getStatus())) {
            return EvaluationOutcome.fail("Order status must be NEW for validation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}

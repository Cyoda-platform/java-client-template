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
public class OrderValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public OrderValidationFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("OrderValidationFailureCriterion initialized with SerializerFactory");
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
        return "OrderValidationFailureCriterion".equals(modelSpec.operationName()) &&
               "order".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Order order) {
        // This criterion represents failure cases complementary to OrderValidationCriterion
        // Implement by checking the negation of the success conditions

        if (order.getOrderId() == null || order.getOrderId().isBlank()) {
            return EvaluationOutcome.success(); // Fail detected above
        }
        if (order.getCustomerName() == null || order.getCustomerName().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (order.getProductCode() == null || order.getProductCode().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            return EvaluationOutcome.success();
        }
        if (order.getOrderDate() == null || order.getOrderDate().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (order.getStatus() == null || order.getStatus().isBlank()) {
            return EvaluationOutcome.success();
        }
        // Business rule: status must be NEW for validation
        if (!"NEW".equalsIgnoreCase(order.getStatus())) {
            return EvaluationOutcome.success();
        }
        return EvaluationOutcome.fail("Order passes all validation but this criterion expects failure", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}

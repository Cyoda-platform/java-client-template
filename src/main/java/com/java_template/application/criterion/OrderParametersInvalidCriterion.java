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
public class OrderParametersInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public OrderParametersInvalidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("OrderParametersInvalidCriterion initialized with SerializerFactory");
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
        return "OrderParametersInvalidCriterion".equals(modelSpec.operationName()) &&
               "order".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Order entity) {
        if (entity.getOrderId() != null && !entity.getOrderId().isBlank()) {
            return EvaluationOutcome.fail("OrderId should be blank or null for invalid parameters", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getCustomerId() != null && !entity.getCustomerId().isBlank()) {
            return EvaluationOutcome.fail("CustomerId should be blank or null for invalid parameters", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getItems() != null && !entity.getItems().isEmpty()) {
            return EvaluationOutcome.fail("Items should be empty or null for invalid parameters", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getShippingAddress() != null && !entity.getShippingAddress().isBlank()) {
            return EvaluationOutcome.fail("Shipping address should be blank or null for invalid parameters", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getPaymentMethod() != null && !entity.getPaymentMethod().isBlank()) {
            return EvaluationOutcome.fail("Payment method should be blank or null for invalid parameters", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getCreatedAt() != null && !entity.getCreatedAt().isBlank()) {
            return EvaluationOutcome.fail("Creation timestamp should be blank or null for invalid parameters", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getStatus() != null && !entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Order status should be blank or null for invalid parameters", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}

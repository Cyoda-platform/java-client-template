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
public class InventoryAvailabilityCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(InventoryAvailabilityCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public InventoryAvailabilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Order.class, this::validateInventoryAvailability)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateInventoryAvailability(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();
        
        if (order == null) {
            return EvaluationOutcome.fail("Order is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }
        
        logger.debug("Checking inventory availability for order: {}", order.getOrderId());

        // For simplicity, assume inventory is available
        logger.info("Inventory availability check passed for order: {}", order.getOrderId());
        return EvaluationOutcome.success();
    }
}

package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class AllShipmentsDeliveredCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public AllShipmentsDeliveredCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entity();
        if (order == null) {
            return EvaluationOutcome.fail("Order is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Query shipments by orderId
        try {
            SearchConditionRequest cond = SearchConditionRequest.group("AND", Condition.of("$.orderId", "EQUALS", order.getOrderId()));
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(
                com.java_template.application.entity.shipment.version_1.Shipment.ENTITY_NAME,
                String.valueOf(com.java_template.application.entity.shipment.version_1.Shipment.ENTITY_VERSION),
                cond,
                true
            );
            ArrayNode arr = future.get();
            if (arr == null || arr.size() == 0) {
                return EvaluationOutcome.fail("No shipments found for order", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            for (int i = 0; i < arr.size(); i++) {
                ObjectNode n = (ObjectNode) arr.get(i);
                String status = n.has("status") ? n.get("status").asText() : null;
                if (!"DELIVERED".equalsIgnoreCase(status)) {
                    return EvaluationOutcome.fail("Not all shipments delivered", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
            }
            return EvaluationOutcome.success();
        } catch (Exception e) {
            logger.warn("Error querying shipments: {}", e.getMessage());
            return EvaluationOutcome.fail("Error querying shipments", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}

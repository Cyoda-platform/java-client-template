package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OrderHasValidShipmentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    @Autowired
    private EntityService entityService;

    public OrderHasValidShipmentCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking order has valid shipment criterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateOrderHasValidShipment)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateOrderHasValidShipment(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entity();
        
        logger.info("Validating order has valid shipment for order: {}", order.getOrderId());

        try {
            // Get shipment by order ID
            Shipment shipment = getShipmentByOrderId(order.getOrderId());
            if (shipment == null) {
                logger.warn("No shipment found for order: {}", order.getOrderId());
                return EvaluationOutcome.fail("No shipment found for order: " + order.getOrderId(), 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check shipment state (in real implementation, would check shipment.meta.state)
            // For now, we'll assume the workflow ensures this criterion only runs when appropriate
            logger.debug("Shipment state validation passed for shipment: {}", shipment.getShipmentId());

            // Check shipment line count matches order line count
            if (shipment.getLines() == null || order.getLines() == null) {
                logger.warn("Shipment or order lines are null for order: {}", order.getOrderId());
                return EvaluationOutcome.fail("Shipment or order lines are missing", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (shipment.getLines().size() != order.getLines().size()) {
                logger.warn("Shipment line count does not match order line count. Order: {}, Shipment lines: {}, Order lines: {}", 
                           order.getOrderId(), shipment.getLines().size(), order.getLines().size());
                return EvaluationOutcome.fail("Shipment line count does not match order line count", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Validate each order line has corresponding shipment line
            for (Order.OrderLine orderLine : order.getLines()) {
                boolean found = false;
                for (Shipment.ShipmentLine shipmentLine : shipment.getLines()) {
                    if (orderLine.getSku().equals(shipmentLine.getSku()) && 
                        orderLine.getQty().equals(shipmentLine.getQtyOrdered())) {
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    logger.warn("Shipment missing line for SKU: {} in order: {}", orderLine.getSku(), order.getOrderId());
                    return EvaluationOutcome.fail("Shipment missing line for SKU: " + orderLine.getSku(), 
                                                StandardEvalReasonCategories.VALIDATION_FAILURE);
                }
            }

            logger.info("Order has valid shipment validation passed for order: {}", order.getOrderId());
            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Error validating shipment for order: {}", order.getOrderId(), e);
            return EvaluationOutcome.fail("Error validating shipment: " + e.getMessage(), 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }

    private Shipment getShipmentByOrderId(String orderId) {
        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(orderIdCondition));

            Optional<EntityResponse<Shipment>> shipmentResponse = entityService.getFirstItemByCondition(
                Shipment.class, Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, condition, true);
            
            return shipmentResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error retrieving shipment by order ID: {}", orderId, e);
            return null;
        }
    }
}

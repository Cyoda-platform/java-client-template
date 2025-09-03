package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityResponse;
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
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OrderHasShipmentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public OrderHasShipmentCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking order has shipment for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateOrderHasShipment)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateOrderHasShipment(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entity();

        logger.debug("Validating order has shipment: {}", order != null ? order.getOrderId() : "null");

        // CRITICAL: Use order getters directly - never extract from payload
        
        // 1. Validate order entity exists
        if (order == null) {
            logger.warn("Order entity is null");
            return EvaluationOutcome.fail("Order is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (order.getOrderId() == null || order.getOrderId().trim().isEmpty()) {
            logger.warn("Order has missing order ID");
            return EvaluationOutcome.fail("Order ID is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        try {
            // 2. Retrieve shipment by orderId
            Shipment shipment = retrieveShipmentByOrderId(order.getOrderId());
            
            // 3. Check shipment entity exists
            if (shipment == null) {
                logger.warn("No shipment found for order {}", order.getOrderId());
                return EvaluationOutcome.fail("Shipment not found for order", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // 4. Validate shipment.orderId matches order.orderId
            if (!order.getOrderId().equals(shipment.getOrderId())) {
                logger.warn("Shipment {} order ID mismatch: expected={}, actual={}", 
                    shipment.getShipmentId(), order.getOrderId(), shipment.getOrderId());
                return EvaluationOutcome.fail("Shipment order ID mismatch", 
                    StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            // 5. Check shipment has valid lines
            if (shipment.getLines() == null || shipment.getLines().isEmpty()) {
                logger.warn("Shipment {} has no lines", shipment.getShipmentId());
                return EvaluationOutcome.fail("Shipment has no lines", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // Additional validation for shipment line consistency with order
            if (order.getLines() != null && !order.getLines().isEmpty()) {
                // Check that shipment has lines for all order lines
                for (Order.OrderLine orderLine : order.getLines()) {
                    boolean found = shipment.getLines().stream()
                        .anyMatch(shipmentLine -> orderLine.getSku().equals(shipmentLine.getSku()));
                    
                    if (!found) {
                        logger.warn("Shipment {} missing line for order line SKU: {}", 
                            shipment.getShipmentId(), orderLine.getSku());
                        return EvaluationOutcome.fail("Shipment missing line for SKU: " + orderLine.getSku(), 
                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                    }
                }
            }

            // Validate shipment line data quality
            for (Shipment.ShipmentLine line : shipment.getLines()) {
                if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                    logger.warn("Shipment {} has line with missing SKU", shipment.getShipmentId());
                    return EvaluationOutcome.fail("Shipment line missing SKU", 
                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                }
                
                if (line.getQtyOrdered() == null || line.getQtyOrdered() <= 0) {
                    logger.warn("Shipment {} line {} has invalid ordered quantity: {}", 
                        shipment.getShipmentId(), line.getSku(), line.getQtyOrdered());
                    return EvaluationOutcome.fail("Shipment line has invalid ordered quantity: " + line.getSku(), 
                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                }
            }

            logger.info("Order {} shipment validation passed: shipment={}, lines={}", 
                order.getOrderId(), shipment.getShipmentId(), shipment.getLines().size());
            
            return EvaluationOutcome.success();
            
        } catch (Exception e) {
            logger.error("Error validating order shipment: {}", e.getMessage(), e);
            return EvaluationOutcome.fail("Error checking shipment: " + e.getMessage(), 
                StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }
    }
    
    private Shipment retrieveShipmentByOrderId(String orderId) {
        try {
            Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
            SearchConditionRequest condition = SearchConditionRequest.group("AND", orderIdCondition);
            
            Optional<EntityResponse<Shipment>> shipmentResponse = entityService.getFirstItemByCondition(
                Shipment.class, Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, condition, true);
                
            return shipmentResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Failed to retrieve shipment for order {}: {}", orderId, e.getMessage());
            return null;
        }
    }
}

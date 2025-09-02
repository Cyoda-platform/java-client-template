package com.java_template.application.criterion;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ShipmentReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public ShipmentReadyCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating ShipmentReadyCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Shipment.class, this::validateShipmentReady)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateShipmentReady(CriterionSerializer.CriterionEntityEvaluationContext<Shipment> context) {
        Shipment shipment = context.entity();

        logger.info("Validating shipment ready criteria for shipment: {}", shipment != null ? shipment.getShipmentId() : "null");

        if (shipment == null) {
            return EvaluationOutcome.fail("Shipment entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check shipment.orderId is not null/empty
        if (shipment.getOrderId() == null || shipment.getOrderId().trim().isEmpty()) {
            return EvaluationOutcome.fail("Shipment order reference is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            // Check referenced order exists
            EntityResponse<Order> orderResponse = entityService.findByBusinessId(Order.class, shipment.getOrderId());
            Order order = orderResponse.getData();
            
            if (order == null) {
                return EvaluationOutcome.fail("Referenced order not found: " + shipment.getOrderId(), StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

        } catch (Exception e) {
            logger.error("Failed to validate referenced order: {}", e.getMessage());
            return EvaluationOutcome.fail("Failed to validate referenced order: " + e.getMessage(), StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check shipment.lines is not empty
        if (shipment.getLines() == null || shipment.getLines().isEmpty()) {
            return EvaluationOutcome.fail("Shipment has no lines", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Extract transition type from request data to determine specific validation
        // For now using hardcoded validation - in a real implementation, this would come from the request payload
        String transitionType = "READY_FOR_DISPATCH"; // TODO: Extract from request payload

        if ("READY_FOR_DISPATCH".equals(transitionType)) {
            // Check all lines have qtyPicked == qtyOrdered
            for (Shipment.ShipmentLine line : shipment.getLines()) {
                if (!line.isFullyPicked()) {
                    return EvaluationOutcome.fail("Not all items have been picked for SKU: " + line.getSku(), 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
            }
        } else if ("DISPATCH_SHIPMENT".equals(transitionType)) {
            // Check all lines have qtyPicked > 0
            for (Shipment.ShipmentLine line : shipment.getLines()) {
                if (line.getQtyPicked() == null || line.getQtyPicked() <= 0) {
                    return EvaluationOutcome.fail("No items have been picked for SKU: " + line.getSku(), 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
            }
        } else if ("CONFIRM_DELIVERY".equals(transitionType)) {
            // Check all lines have qtyShipped > 0
            for (Shipment.ShipmentLine line : shipment.getLines()) {
                if (line.getQtyShipped() == null || line.getQtyShipped() <= 0) {
                    return EvaluationOutcome.fail("No items have been shipped for SKU: " + line.getSku(), 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
            }
        }

        logger.info("Shipment ready validation passed: shipmentId={}, orderId={}, transitionType={}", 
            shipment.getShipmentId(), shipment.getOrderId(), transitionType);

        return EvaluationOutcome.success();
    }
}

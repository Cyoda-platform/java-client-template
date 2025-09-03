package com.java_template.application.criterion;

import com.java_template.application.entity.shipment.version_1.Shipment;
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
public class ShipmentQuantitiesValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ShipmentQuantitiesValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking shipment quantities valid for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Shipment.class, this::validateShipmentQuantities)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateShipmentQuantities(CriterionSerializer.CriterionEntityEvaluationContext<Shipment> context) {
        Shipment shipment = context.entity();

        logger.debug("Validating shipment quantities: {}", shipment != null ? shipment.getShipmentId() : "null");

        // CRITICAL: Use shipment getters directly - never extract from payload
        
        // 1. Validate shipment entity exists
        if (shipment == null) {
            logger.warn("Shipment entity is null");
            return EvaluationOutcome.fail("Shipment is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (shipment.getShipmentId() == null || shipment.getShipmentId().trim().isEmpty()) {
            logger.warn("Shipment has missing shipment ID");
            return EvaluationOutcome.fail("Shipment ID is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // 2. Check shipment.lines is not null and not empty
        if (shipment.getLines() == null) {
            logger.warn("Shipment {} has null lines", shipment.getShipmentId());
            return EvaluationOutcome.fail("Shipment lines array is null", 
                StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (shipment.getLines().isEmpty()) {
            logger.warn("Shipment {} has empty lines array", shipment.getShipmentId());
            return EvaluationOutcome.fail("Shipment has no lines", 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // 3. For each line in shipment.lines, validate quantities
        for (Shipment.ShipmentLine line : shipment.getLines()) {
            if (line == null) {
                logger.warn("Shipment {} has null line", shipment.getShipmentId());
                return EvaluationOutcome.fail("Shipment contains null line", 
                    StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            String sku = line.getSku();
            if (sku == null || sku.trim().isEmpty()) {
                logger.warn("Shipment {} has line with missing SKU", shipment.getShipmentId());
                return EvaluationOutcome.fail("Shipment line missing SKU", 
                    StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            // 3a. Validate qtyOrdered > 0
            if (line.getQtyOrdered() == null || line.getQtyOrdered() <= 0) {
                logger.warn("Shipment {} line {} has invalid ordered quantity: {}", 
                    shipment.getShipmentId(), sku, line.getQtyOrdered());
                return EvaluationOutcome.fail("Line " + sku + " has invalid ordered quantity", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // 3b. Validate qtyPicked >= 0 and qtyPicked <= qtyOrdered
            if (line.getQtyPicked() == null || line.getQtyPicked() < 0) {
                logger.warn("Shipment {} line {} has invalid picked quantity: {}", 
                    shipment.getShipmentId(), sku, line.getQtyPicked());
                return EvaluationOutcome.fail("Line " + sku + " has negative picked quantity", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            if (line.getQtyPicked() > line.getQtyOrdered()) {
                logger.warn("Shipment {} line {} picked quantity {} exceeds ordered quantity {}", 
                    shipment.getShipmentId(), sku, line.getQtyPicked(), line.getQtyOrdered());
                return EvaluationOutcome.fail("Line " + sku + " picked quantity exceeds ordered quantity", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // 3c. Validate qtyShipped >= 0 and qtyShipped <= qtyPicked
            if (line.getQtyShipped() == null || line.getQtyShipped() < 0) {
                logger.warn("Shipment {} line {} has invalid shipped quantity: {}", 
                    shipment.getShipmentId(), sku, line.getQtyShipped());
                return EvaluationOutcome.fail("Line " + sku + " has negative shipped quantity", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            if (line.getQtyShipped() > line.getQtyPicked()) {
                logger.warn("Shipment {} line {} shipped quantity {} exceeds picked quantity {}", 
                    shipment.getShipmentId(), sku, line.getQtyShipped(), line.getQtyPicked());
                return EvaluationOutcome.fail("Line " + sku + " shipped quantity exceeds picked quantity", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            logger.debug("Shipment {} line {} quantities valid: ordered={}, picked={}, shipped={}", 
                shipment.getShipmentId(), sku, line.getQtyOrdered(), line.getQtyPicked(), line.getQtyShipped());
        }

        // Additional validation for shipment completeness
        if (shipment.getOrderId() == null || shipment.getOrderId().trim().isEmpty()) {
            logger.warn("Shipment {} has missing order ID", shipment.getShipmentId());
            return EvaluationOutcome.fail("Shipment order ID is missing", 
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.info("Shipment {} quantities validation passed: {} lines validated", 
            shipment.getShipmentId(), shipment.getLines().size());
        
        return EvaluationOutcome.success();
    }
}

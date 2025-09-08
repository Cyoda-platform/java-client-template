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

import java.util.Arrays;
import java.util.List;

/**
 * ShipmentCriterion - Validates shipment business rules
 * 
 * This criterion validates:
 * - Required fields are present and valid
 * - Shipment status is valid
 * - Shipment lines are valid
 * - Quantity relationships are logical (picked <= ordered, shipped <= picked)
 * - Order reference is valid
 */
@Component
public class ShipmentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final List<String> VALID_SHIPMENT_STATUSES = Arrays.asList(
        "PICKING", "WAITING_TO_SEND", "SENT", "DELIVERED"
    );

    public ShipmentCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Shipment criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Shipment.class, this::validateShipment)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the Shipment entity
     */
    private EvaluationOutcome validateShipment(CriterionSerializer.CriterionEntityEvaluationContext<Shipment> context) {
        Shipment shipment = context.entityWithMetadata().entity();

        // Check if shipment is null (structural validation)
        if (shipment == null) {
            logger.warn("Shipment is null");
            return EvaluationOutcome.fail("Shipment is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Basic entity validation
        if (!shipment.isValid()) {
            logger.warn("Shipment basic validation failed for shipmentId: {}", shipment.getShipmentId());
            return EvaluationOutcome.fail("Shipment basic validation failed", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate shipment status
        if (!VALID_SHIPMENT_STATUSES.contains(shipment.getStatus())) {
            logger.warn("Invalid shipment status: {}", shipment.getStatus());
            return EvaluationOutcome.fail("Invalid shipment status: " + shipment.getStatus(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate shipment lines
        if (shipment.getLines() == null || shipment.getLines().isEmpty()) {
            logger.warn("Shipment must have at least one line item");
            return EvaluationOutcome.fail("Shipment must have at least one line item", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        for (Shipment.ShipmentLine line : shipment.getLines()) {
            EvaluationOutcome lineValidation = validateShipmentLine(line);
            if (!lineValidation.isSuccess()) {
                return lineValidation;
            }
        }

        // Validate shipment ID format
        if (shipment.getShipmentId() != null && (shipment.getShipmentId().length() < 3 || shipment.getShipmentId().length() > 50)) {
            logger.warn("Shipment ID length invalid: {}", shipment.getShipmentId());
            return EvaluationOutcome.fail("Shipment ID must be between 3 and 50 characters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate order ID format
        if (shipment.getOrderId() != null && (shipment.getOrderId().length() < 3 || shipment.getOrderId().length() > 50)) {
            logger.warn("Order ID length invalid: {}", shipment.getOrderId());
            return EvaluationOutcome.fail("Order ID must be between 3 and 50 characters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates individual shipment line
     */
    private EvaluationOutcome validateShipmentLine(Shipment.ShipmentLine line) {
        if (line == null) {
            return EvaluationOutcome.fail("Shipment line is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (line.getSku() == null || line.getSku().trim().isEmpty()) {
            return EvaluationOutcome.fail("Shipment line SKU is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (line.getQtyOrdered() == null || line.getQtyOrdered() <= 0) {
            return EvaluationOutcome.fail("Shipment line ordered quantity must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (line.getQtyPicked() == null || line.getQtyPicked() < 0) {
            return EvaluationOutcome.fail("Shipment line picked quantity cannot be negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (line.getQtyShipped() == null || line.getQtyShipped() < 0) {
            return EvaluationOutcome.fail("Shipment line shipped quantity cannot be negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate quantity relationships
        if (line.getQtyPicked() > line.getQtyOrdered()) {
            return EvaluationOutcome.fail("Picked quantity cannot exceed ordered quantity", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (line.getQtyShipped() > line.getQtyPicked()) {
            return EvaluationOutcome.fail("Shipped quantity cannot exceed picked quantity", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}

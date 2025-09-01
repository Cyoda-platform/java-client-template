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
public class ShipmentReadyForDeliveryCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ShipmentReadyForDeliveryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking ShipmentReadyForDeliveryCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Shipment.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Shipment> context) {
        Shipment shipment = context.entity();
        
        logger.info("Validating shipment ready for delivery: {}", shipment != null ? shipment.getShipmentId() : "null");

        // Check if shipment entity exists
        if (shipment == null) {
            logger.warn("Shipment entity not found");
            return EvaluationOutcome.fail("Shipment entity not found", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Note: State validation is handled by the workflow system
        // This criterion focuses on business logic validation

        // Check if shipment.lines is not empty
        if (shipment.getLines() == null || shipment.getLines().isEmpty()) {
            logger.warn("Shipment must have line items");
            return EvaluationOutcome.fail("Shipment must have line items", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate each shipment line
        for (Shipment.ShipmentLine line : shipment.getLines()) {
            // Check if line.qtyShipped > 0
            if (line.getQtyShipped() == null || line.getQtyShipped() <= 0) {
                logger.warn("All items must be shipped before delivery. SKU: {} has shipped qty: {}", 
                           line.getSku(), line.getQtyShipped());
                return EvaluationOutcome.fail("All items must be shipped before delivery", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
            
            // Check if line.qtyShipped = line.qtyOrdered
            if (!line.getQtyShipped().equals(line.getQtyOrdered())) {
                logger.warn("Shipped quantity must equal ordered quantity. SKU: {} - shipped: {}, ordered: {}", 
                           line.getSku(), line.getQtyShipped(), line.getQtyOrdered());
                return EvaluationOutcome.fail("Shipped quantity must equal ordered quantity", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        logger.info("Shipment validation successful for shipment: {}", shipment.getShipmentId());
        return EvaluationOutcome.success();
    }
}

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
public class ShipmentValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ShipmentValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Shipment.class, this::validateShipment)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateShipment(CriterionSerializer.CriterionEntityEvaluationContext<Shipment> context) {
        Shipment shipment = context.entity();

        if (shipment == null) {
            return EvaluationOutcome.fail("Shipment entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (shipment.getOrderId() == null || shipment.getOrderId().trim().isEmpty()) {
            return EvaluationOutcome.fail("Shipment must have valid order ID", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (shipment.getLines() == null || shipment.getLines().isEmpty()) {
            return EvaluationOutcome.fail("Shipment must have line items", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        for (Shipment.ShipmentLine line : shipment.getLines()) {
            if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                return EvaluationOutcome.fail("Shipment line must have valid SKU", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (line.getQtyOrdered() == null || line.getQtyOrdered() <= 0) {
                return EvaluationOutcome.fail("Shipment line ordered quantity must be positive", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (line.getQtyPicked() == null || line.getQtyPicked() < 0) {
                return EvaluationOutcome.fail("Shipment line picked quantity cannot be negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (line.getQtyShipped() == null || line.getQtyShipped() < 0) {
                return EvaluationOutcome.fail("Shipment line shipped quantity cannot be negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (line.getQtyPicked() > line.getQtyOrdered()) {
                return EvaluationOutcome.fail("Picked quantity cannot exceed ordered quantity", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (line.getQtyShipped() > line.getQtyPicked()) {
                return EvaluationOutcome.fail("Shipped quantity cannot exceed picked quantity", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}

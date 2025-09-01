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
public class ShipmentCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ShipmentCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking shipment complete criterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Shipment.class, this::validateShipmentComplete)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateShipmentComplete(CriterionSerializer.CriterionEntityEvaluationContext<Shipment> context) {
        Shipment shipment = context.entity();

        // Check if shipment exists
        if (shipment == null) {
            return EvaluationOutcome.fail("Shipment entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if shipment ID is present
        if (shipment.getShipmentId() == null || shipment.getShipmentId().isBlank()) {
            return EvaluationOutcome.fail("Shipment ID is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if shipment is in SENT status (ready for delivery)
        if (!"SENT".equals(shipment.getStatus())) {
            return EvaluationOutcome.fail("Shipment is not in SENT status, current status: " + shipment.getStatus(),
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if all lines have been shipped
        if (shipment.getLines() != null) {
            for (Shipment.Line line : shipment.getLines()) {
                if (line.getQtyShipped() == null || line.getQtyOrdered() == null) {
                    return EvaluationOutcome.fail("Shipment line quantities are incomplete for SKU: " + line.getSku(),
                                                StandardEvalReasonCategories.VALIDATION_FAILURE);
                }

                if (line.getQtyShipped() < line.getQtyOrdered()) {
                    return EvaluationOutcome.fail("Not all items shipped for SKU: " + line.getSku() +
                                                " (shipped: " + line.getQtyShipped() + ", ordered: " + line.getQtyOrdered() + ")",
                                                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
            }
        }

        logger.info("Shipment {} is complete and ready for delivery", shipment.getShipmentId());
        return EvaluationOutcome.success();
    }
}
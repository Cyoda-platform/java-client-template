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
public class ShipmentReadyForDispatchCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ShipmentReadyForDispatchCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
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

        if (shipment.getLines() == null || shipment.getLines().isEmpty()) {
            return EvaluationOutcome.fail("Shipment has no line items", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        for (Shipment.ShipmentLine line : shipment.getLines()) {
            if (line.getQtyPicked() == null || line.getQtyPicked() < 0) {
                return EvaluationOutcome.fail("Invalid picked quantity for item " + line.getSku(), StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (line.getQtyOrdered() == null || line.getQtyPicked() < line.getQtyOrdered()) {
                return EvaluationOutcome.fail("Item " + line.getSku() + " not fully picked", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}
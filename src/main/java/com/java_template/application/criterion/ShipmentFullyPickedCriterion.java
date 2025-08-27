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
public class ShipmentFullyPickedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ShipmentFullyPickedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Shipment.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Shipment> context) {
         Shipment entity = context.entity();

         if (entity == null) {
             logger.warn("Shipment entity is null in evaluation context");
             return EvaluationOutcome.fail("Shipment entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (entity.getLines() == null || entity.getLines().isEmpty()) {
             logger.warn("Shipment {} has no lines", entity.getShipmentId());
             return EvaluationOutcome.fail("Shipment has no lines", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         StringBuilder notPickedSummary = new StringBuilder();
         boolean allPicked = true;

         for (Shipment.Line line : entity.getLines()) {
             if (line == null) {
                 logger.warn("Shipment {} contains a null line entry", entity.getShipmentId());
                 return EvaluationOutcome.fail("Shipment contains an invalid line", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             Integer qtyOrdered = line.getQtyOrdered();
             if (qtyOrdered == null) {
                 logger.warn("Shipment {} line {} missing qtyOrdered", entity.getShipmentId(), line.getSku());
                 return EvaluationOutcome.fail("Line missing qtyOrdered for sku: " + line.getSku(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (qtyOrdered < 0) {
                 logger.warn("Shipment {} line {} has negative qtyOrdered {}", entity.getShipmentId(), line.getSku(), qtyOrdered);
                 return EvaluationOutcome.fail("Invalid negative qtyOrdered for sku: " + line.getSku(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             Integer qtyPicked = line.getQtyPicked() == null ? 0 : line.getQtyPicked();
             if (qtyPicked < 0) {
                 logger.warn("Shipment {} line {} has negative qtyPicked {}", entity.getShipmentId(), line.getSku(), qtyPicked);
                 return EvaluationOutcome.fail("Invalid negative qtyPicked for sku: " + line.getSku(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             if (qtyPicked < qtyOrdered) {
                 allPicked = false;
                 if (notPickedSummary.length() > 0) notPickedSummary.append("; ");
                 notPickedSummary.append(String.format("%s: %d/%d", line.getSku(), qtyPicked, qtyOrdered));
             }
         }

         if (!allPicked) {
             String msg = "Not all shipment lines picked (" + notPickedSummary.toString() + ")";
             logger.debug("Shipment {} NOT fully picked: {}", entity.getShipmentId(), notPickedSummary.toString());
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         logger.debug("Shipment {} is fully picked", entity.getShipmentId());
         return EvaluationOutcome.success();
    }
}
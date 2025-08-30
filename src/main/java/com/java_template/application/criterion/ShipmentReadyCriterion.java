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
public class ShipmentReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ShipmentReadyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Shipment.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Shipment> context) {
         Shipment entity = context.entity();

         if (entity == null) {
             logger.warn("Shipment entity is null in context");
             return EvaluationOutcome.fail("Shipment entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Must be in PICKING state to be considered for READY -> WAITING_TO_SEND transition
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Shipment status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"PICKING".equals(status)) {
             // Require exact PICKING state before marking ready
             return EvaluationOutcome.fail("Shipment must be in PICKING state to be ready", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getLines() == null || entity.getLines().isEmpty()) {
             return EvaluationOutcome.fail("Shipment must have at least one line", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         for (Shipment.ShipmentLine line : entity.getLines()) {
             if (line == null) {
                 return EvaluationOutcome.fail("Shipment contains a null line", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             String sku = line.getSku();
             if (sku == null || sku.isBlank()) {
                 return EvaluationOutcome.fail("Shipment line missing sku", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             Integer qtyOrdered = line.getQtyOrdered();
             Integer qtyPicked = line.getQtyPicked();
             if (qtyOrdered == null) {
                 return EvaluationOutcome.fail("Shipment line " + sku + " missing qtyOrdered", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (qtyPicked == null) {
                 return EvaluationOutcome.fail("Shipment line " + sku + " missing qtyPicked", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             if (qtyPicked > qtyOrdered) {
                 return EvaluationOutcome.fail(
                     "Shipment line " + sku + " has qtyPicked greater than qtyOrdered (" + qtyPicked + ">" + qtyOrdered + ")",
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (qtyPicked < qtyOrdered) {
                 return EvaluationOutcome.fail(
                     "Shipment line " + sku + " not fully picked (" + qtyPicked + " of " + qtyOrdered + ")",
                     StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // All checks passed: every line fully picked and shipment in PICKING state
         return EvaluationOutcome.success();
    }
}
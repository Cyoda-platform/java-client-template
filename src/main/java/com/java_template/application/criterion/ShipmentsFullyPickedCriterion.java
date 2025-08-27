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

import java.util.List;
import java.util.Objects;

@Component
public class ShipmentsFullyPickedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ShipmentsFullyPickedCriterion(SerializerFactory serializerFactory) {
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
        // Use exact criterion name match (case-sensitive) as required
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Shipment> context) {
         Shipment shipment = context.entity();
         if (shipment == null) {
             logger.debug("Shipment entity is null in context");
             return EvaluationOutcome.fail("Shipment entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If status is absent treat as data quality issue
         String status = shipment.getStatus();
         if (status == null || status.isBlank()) {
             logger.debug("Shipment {} has no status", shipment.getShipmentId());
             return EvaluationOutcome.fail("Shipment status is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Criterion only concerns shipments that are currently in PICKING state.
         // If shipment is in any other state, consider the criterion satisfied (no picking required).
         if (!"PICKING".equals(status)) {
             logger.debug("Shipment {} in state '{}' - skipping pick validation", shipment.getShipmentId(), status);
             return EvaluationOutcome.success();
         }

         List<Shipment.Line> lines = shipment.getLines();
         if (lines == null || lines.isEmpty()) {
             logger.debug("Shipment {} has no lines", shipment.getShipmentId());
             return EvaluationOutcome.fail("Shipment has no lines", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         StringBuilder notFullyPicked = new StringBuilder();
         boolean dataQualityIssue = false;

         for (Shipment.Line line : lines) {
             if (line == null) {
                 // unexpected but treat as data quality problem
                 logger.debug("Shipment {} contains null line element", shipment.getShipmentId());
                 dataQualityIssue = true;
                 if (notFullyPicked.length() > 0) notFullyPicked.append("; ");
                 notFullyPicked.append("null-line");
                 continue;
             }
             Integer qtyOrdered = line.getQtyOrdered();
             Integer qtyPicked = line.getQtyPicked();
             String sku = line.getSku();

             if (qtyOrdered == null) {
                 logger.debug("Shipment {} line {} has null qtyOrdered", shipment.getShipmentId(), sku);
                 dataQualityIssue = true;
                 if (notFullyPicked.length() > 0) notFullyPicked.append("; ");
                 notFullyPicked.append(String.format("%s:missing-qtyOrdered", Objects.toString(sku, "<unknown>")));
                 continue;
             }

             int picked = (qtyPicked == null) ? 0 : qtyPicked;
             if (picked < qtyOrdered) {
                 if (notFullyPicked.length() > 0) notFullyPicked.append("; ");
                 notFullyPicked.append(String.format("%s(picked=%d/ordered=%d)", Objects.toString(sku, "<unknown>"), picked, qtyOrdered));
             }
         }

         if (dataQualityIssue) {
             String msg = "Shipment contains invalid line data: " + notFullyPicked.toString();
             logger.debug("Shipment {} data quality issues: {}", shipment.getShipmentId(), msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (notFullyPicked.length() > 0) {
             String msg = "Not all shipment lines fully picked: " + notFullyPicked.toString();
             logger.debug("Shipment {} not fully picked: {}", shipment.getShipmentId(), msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All lines fully picked -> criterion satisfied
         logger.debug("Shipment {} fully picked", shipment.getShipmentId());
         return EvaluationOutcome.success();
    }
}
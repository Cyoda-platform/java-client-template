package com.java_template.application.criterion;

import com.java_template.application.entity.inventoryitem.version_1.InventoryItem;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Component
public class ValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(InventoryItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<InventoryItem> context) {
         InventoryItem entity = context.entity();
         if (entity == null) {
             logger.warn("ValidationFailedCriterion: entity is null in context {}", context);
             return EvaluationOutcome.fail("InventoryItem entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required string fields
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Numeric fields
         if (entity.getQuantity() == null) {
             return EvaluationOutcome.fail("quantity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getQuantity() < 0) {
             return EvaluationOutcome.fail("quantity must be >= 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPrice() == null) {
             return EvaluationOutcome.fail("price is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPrice() < 0.0) {
             return EvaluationOutcome.fail("price must be >= 0.0", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // dateAdded is optional but if present must be ISO date (yyyy-MM-dd)
         if (entity.getDateAdded() != null && !entity.getDateAdded().isBlank()) {
             try {
                 LocalDate.parse(entity.getDateAdded()); // ISO_LOCAL_DATE
             } catch (DateTimeParseException ex) {
                 return EvaluationOutcome.fail("dateAdded must be ISO date (yyyy-MM-dd)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // status should conform to expected workflow states (case-insensitive)
         Set<String> allowedStatuses = Set.of("INGESTED", "VALIDATED", "INVALID", "INVALIDATED");
         String statusUpper = entity.getStatus().toUpperCase();
         if (!allowedStatuses.contains(statusUpper)) {
             // Business rule: status must be one of allowed workflow values
             return EvaluationOutcome.fail("status has invalid value", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Category, location, supplier are optional per functional requirements - no validation here.

         // All checks passed - entity is considered valid for this criterion
         return EvaluationOutcome.success();
    }
}
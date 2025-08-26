package com.java_template.application.criterion;

import com.java_template.application.entity.laureate.version_1.Laureate;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class ValidationPassedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationPassedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getId() == null) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
             return EvaluationOutcome.fail("firstname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getSurname() == null || entity.getSurname().isBlank()) {
             return EvaluationOutcome.fail("surname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
             return EvaluationOutcome.fail("category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getYear() == null || entity.getYear().isBlank()) {
             return EvaluationOutcome.fail("year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // year should be numeric (e.g., "2010")
         try {
             Integer.parseInt(entity.getYear());
         } catch (NumberFormatException nfe) {
             return EvaluationOutcome.fail("year must be numeric", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // createdAt should be ISO-8601 timestamp
         try {
             Instant.parse(entity.getCreatedAt());
         } catch (DateTimeParseException dtpe) {
             return EvaluationOutcome.fail("createdAt must be an ISO-8601 timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getSourceJobId() == null || entity.getSourceJobId().isBlank()) {
             return EvaluationOutcome.fail("sourceJobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Optional date fields: validate format if present
         if (entity.getBorn() != null && !entity.getBorn().isBlank()) {
             try {
                 LocalDate.parse(entity.getBorn());
             } catch (DateTimeParseException dtpe) {
                 return EvaluationOutcome.fail("born must be an ISO date (yyyy-MM-dd)", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         if (entity.getDied() != null && !entity.getDied().isBlank()) {
             try {
                 LocalDate.parse(entity.getDied());
             } catch (DateTimeParseException dtpe) {
                 return EvaluationOutcome.fail("died must be an ISO date (yyyy-MM-dd) or null", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}
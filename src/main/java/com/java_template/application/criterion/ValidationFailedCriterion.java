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
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

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

         List<String> errors = new ArrayList<>();

         // Required fields
         if (entity.getId() == null) {
             errors.add("id is required");
         }
         if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
             errors.add("firstname is required");
         }
         if (entity.getSurname() == null || entity.getSurname().isBlank()) {
             errors.add("surname is required");
         }
         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
             errors.add("category is required");
         }
         if (entity.getYear() == null || entity.getYear().isBlank()) {
             errors.add("year is required");
         } else {
             // Validate year is a reasonable numeric year (e.g., 1800..next year)
             try {
                 int y = Integer.parseInt(entity.getYear());
                 int currentYear = LocalDate.now().getYear();
                 if (y < 1800 || y > currentYear + 1) {
                     errors.add("year is out of expected range");
                 }
             } catch (NumberFormatException ex) {
                 errors.add("year must be numeric");
             }
         }
         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             errors.add("createdAt is required");
         } else {
             // Validate ISO-8601 timestamp
             try {
                 Instant.parse(entity.getCreatedAt());
             } catch (DateTimeParseException ex) {
                 errors.add("createdAt must be ISO-8601 timestamp (e.g., 2025-08-26T10:00:05Z)");
             }
         }
         if (entity.getSourceJobId() == null || entity.getSourceJobId().isBlank()) {
             errors.add("sourceJobId is required");
         }

         // Optional date validations
         LocalDate bornDate = null;
         if (entity.getBorn() != null && !entity.getBorn().isBlank()) {
             try {
                 bornDate = LocalDate.parse(entity.getBorn());
             } catch (DateTimeParseException ex) {
                 errors.add("born must be ISO date (yyyy-MM-dd)");
             }
         }
         LocalDate diedDate = null;
         if (entity.getDied() != null && !entity.getDied().isBlank()) {
             try {
                 diedDate = LocalDate.parse(entity.getDied());
             } catch (DateTimeParseException ex) {
                 errors.add("died must be ISO date (yyyy-MM-dd) or null");
             }
         }
         if (bornDate != null && diedDate != null) {
             if (diedDate.isBefore(bornDate)) {
                 errors.add("died must be on or after born");
             }
         }

         // Age consistency check (if provided)
         if (entity.getAge() != null && bornDate != null) {
             LocalDate reference = (diedDate != null) ? diedDate : LocalDate.now();
             int computedAge = Period.between(bornDate, reference).getYears();
             if (computedAge < 0) {
                 errors.add("computed age is negative based on born/died dates");
             } else if (Math.abs(computedAge - entity.getAge()) > 1) { // allow off-by-one due to month/day differences
                 errors.add("age is inconsistent with born/died dates");
             }
         }

         if (errors.isEmpty()) {
             return EvaluationOutcome.success();
         } else {
             String message = String.join("; ", errors);
             logger.debug("ValidationFailedCriterion for Laureate[id={}] reasons: {}", entity.getId(), message);
             return EvaluationOutcome.fail(message, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
    }
}
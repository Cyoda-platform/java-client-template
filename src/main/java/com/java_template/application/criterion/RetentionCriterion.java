package com.java_template.application.criterion;

import com.java_template.application.entity.report.version_1.Report;
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

import java.time.*;
import java.time.format.DateTimeParseException;

@Component
public class RetentionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // retention threshold in days
    private static final long RETENTION_DAYS = 365L;

    public RetentionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Report.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Use exact match (case-sensitive) for criterion name as required
        return modelSpec != null && className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
         Report entity = context.entity();

         // Basic data quality checks required for retention decision
         if (entity == null) {
             return EvaluationOutcome.fail("Report entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // storageLocation must exist to allow archival (where to store archived artifact)
         if (entity.getStorageLocation() == null || entity.getStorageLocation().isBlank()) {
             return EvaluationOutcome.fail("Missing storageLocation - cannot perform retention/archive", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // generatedAt must be present and parseable
         String generatedAt = entity.getGeneratedAt();
         if (generatedAt == null || generatedAt.isBlank()) {
             return EvaluationOutcome.fail("generatedAt is required for retention calculation", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Instant generatedInstant;
         try {
             // Try several common ISO parse strategies
             try {
                 generatedInstant = Instant.parse(generatedAt);
             } catch (DateTimeParseException ex1) {
                 // try OffsetDateTime
                 try {
                     generatedInstant = OffsetDateTime.parse(generatedAt).toInstant();
                 } catch (DateTimeParseException ex2) {
                     // try LocalDate
                     try {
                         LocalDate d = LocalDate.parse(generatedAt);
                         generatedInstant = d.atStartOfDay(ZoneOffset.UTC).toInstant();
                     } catch (DateTimeParseException ex3) {
                         throw ex3;
                     }
                 }
             }
         } catch (DateTimeParseException e) {
             logger.warn("Unable to parse generatedAt '{}'", generatedAt, e);
             return EvaluationOutcome.fail("generatedAt is not a valid ISO date/time", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate metrics presence (required to decide retention quality)
         if (entity.getMetrics() == null) {
             return EvaluationOutcome.fail("Missing metrics required for retention decision", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getMetrics().getTotalItems() == null
             || entity.getMetrics().getTotalQuantity() == null
             || entity.getMetrics().getAveragePrice() == null
             || entity.getMetrics().getTotalValue() == null) {
             return EvaluationOutcome.fail("Incomplete metrics in report", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: only archive reports older than RETENTION_DAYS
         Instant now = Instant.now();
         long daysOld = Duration.between(generatedInstant, now).toDays();
         if (daysOld < RETENTION_DAYS) {
             return EvaluationOutcome.fail(
                 String.format("Report is only %d days old; requires at least %d days for retention/archive", daysOld, RETENTION_DAYS),
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
             );
         }

         // All checks passed -> eligible for retention/archive
         return EvaluationOutcome.success();
    }
}
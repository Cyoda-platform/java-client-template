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

import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeParseException;

@Component
public class ValidationSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationSuccessCriterion(SerializerFactory serializerFactory) {
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
             logger.debug("Laureate entity is null in validation context");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: id
         if (entity.getId() == null) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getId() != null && entity.getId() <= 0) {
             return EvaluationOutcome.fail("id must be a positive integer", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: firstname
         if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
             return EvaluationOutcome.fail("firstname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: surname
         if (entity.getSurname() == null || entity.getSurname().isBlank()) {
             return EvaluationOutcome.fail("surname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: category
         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
             return EvaluationOutcome.fail("category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: year (must be numeric and in a reasonable range)
         String yearStr = entity.getYear();
         if (yearStr == null || yearStr.isBlank()) {
             return EvaluationOutcome.fail("year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         int parsedYear;
         try {
             parsedYear = Integer.parseInt(yearStr.trim());
         } catch (NumberFormatException nfe) {
             return EvaluationOutcome.fail("year must be a numeric value", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         int currentYear = Year.now().getValue();
         if (parsedYear < 1800 || parsedYear > currentYear + 1) {
             return EvaluationOutcome.fail("year is out of expected range", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // born (if present) must be a valid ISO date (yyyy-MM-dd)
         String born = entity.getBorn();
         if (born != null && !born.isBlank()) {
             try {
                 LocalDate.parse(born);
             } catch (DateTimeParseException dte) {
                 return EvaluationOutcome.fail("born must be an ISO date (yyyy-MM-dd)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // died (if present) must be a valid ISO date (yyyy-MM-dd)
         String died = entity.getDied();
         if (died != null && !died.isBlank()) {
             try {
                 LocalDate.parse(died);
             } catch (DateTimeParseException dte) {
                 return EvaluationOutcome.fail("died must be an ISO date (yyyy-MM-dd) or null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // ingestJobId, if present, must not be blank
         if (entity.getIngestJobId() != null && entity.getIngestJobId().isBlank()) {
             return EvaluationOutcome.fail("ingestJobId, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic sanity: if born date and year present, ensure born year <= award year
         if (born != null && !born.isBlank()) {
             try {
                 LocalDate bornDate = LocalDate.parse(born);
                 if (parsedYear < bornDate.getYear()) {
                     return EvaluationOutcome.fail("award year is earlier than birth year", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             } catch (DateTimeParseException ignored) {
                 // already handled above; ignore here
             }
         }

        return EvaluationOutcome.success();
    }
}
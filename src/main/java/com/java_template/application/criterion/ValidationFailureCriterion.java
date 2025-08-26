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
import java.time.format.DateTimeParseException;

@Component
public class ValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();

         if (entity == null) {
             logger.debug("ValidationFailureCriterion: entity is null");
             return EvaluationOutcome.fail("Laureate entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // id is required and must be a positive integer
         if (entity.getId() == null) {
             return EvaluationOutcome.fail("Laureate.id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getId() != null && entity.getId() <= 0) {
             return EvaluationOutcome.fail("Laureate.id must be a positive integer", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // year is required and must be a 4-digit year
         if (entity.getYear() == null || entity.getYear().isBlank()) {
             return EvaluationOutcome.fail("Laureate.year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String year = entity.getYear().trim();
         if (!year.matches("\\d{4}")) {
             return EvaluationOutcome.fail("Laureate.year must be a 4-digit year", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // category required
         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
             return EvaluationOutcome.fail("Laureate.category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // must have either full name or firstname+surname
         boolean hasFullName = entity.getName() != null && !entity.getName().isBlank();
         boolean hasParts = entity.getFirstname() != null && !entity.getFirstname().isBlank()
                 && entity.getSurname() != null && !entity.getSurname().isBlank();
         if (!hasFullName && !hasParts) {
             return EvaluationOutcome.fail("Laureate must have either name or firstname and surname", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // born date if present must be ISO date (yyyy-MM-dd or parsable by LocalDate)
         if (entity.getBorn() != null && !entity.getBorn().isBlank()) {
             String born = entity.getBorn().trim();
             try {
                 LocalDate.parse(born);
             } catch (DateTimeParseException e) {
                 logger.debug("Invalid born date for laureate id {}: {}", entity.getId(), born);
                 return EvaluationOutcome.fail("Laureate.born must be an ISO date (yyyy-MM-dd)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // died date if present must be ISO date
         if (entity.getDied() != null && !entity.getDied().isBlank()) {
             String died = entity.getDied().trim();
             try {
                 LocalDate.parse(died);
             } catch (DateTimeParseException e) {
                 logger.debug("Invalid died date for laureate id {}: {}", entity.getId(), died);
                 return EvaluationOutcome.fail("Laureate.died must be an ISO date (yyyy-MM-dd) or null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // ageAtAward if present must be non-negative
         if (entity.getAgeAtAward() != null && entity.getAgeAtAward() < 0) {
             return EvaluationOutcome.fail("Laureate.ageAtAward must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // basic validation passed
         return EvaluationOutcome.success();
    }
}
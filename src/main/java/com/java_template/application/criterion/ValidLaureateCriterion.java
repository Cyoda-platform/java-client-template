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
public class ValidLaureateCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidLaureateCriterion(SerializerFactory serializerFactory) {
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
        // Must match criterion name exactly
        return "ValidLaureateCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate laureate = context.entity();

         if (laureate == null) {
             return EvaluationOutcome.fail("Laureate entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields
         if (laureate.getId() == null) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getFirstname() == null || laureate.getFirstname().isBlank()) {
             return EvaluationOutcome.fail("firstname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getSurname() == null || laureate.getSurname().isBlank()) {
             return EvaluationOutcome.fail("surname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getCategory() == null || laureate.getCategory().isBlank()) {
             return EvaluationOutcome.fail("category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getYear() == null || laureate.getYear().isBlank()) {
             return EvaluationOutcome.fail("year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getIngestJobId() == null || laureate.getIngestJobId().isBlank()) {
             return EvaluationOutcome.fail("ingestJobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Year should be numeric (simple validation)
         String yearStr = laureate.getYear();
         try {
             Integer.parseInt(yearStr);
         } catch (NumberFormatException nfe) {
             return EvaluationOutcome.fail("year must be a numeric value", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // born date, if provided, must be ISO date (yyyy-MM-dd)
         String born = laureate.getBorn();
         if (born != null && !born.isBlank()) {
             try {
                 LocalDate.parse(born);
             } catch (DateTimeParseException dte) {
                 return EvaluationOutcome.fail("born must be ISO date (yyyy-MM-dd) when provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // computedAge, if present, must be non-negative
         Integer computedAge = laureate.getComputedAge();
         if (computedAge != null && computedAge < 0) {
             return EvaluationOutcome.fail("computedAge must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}
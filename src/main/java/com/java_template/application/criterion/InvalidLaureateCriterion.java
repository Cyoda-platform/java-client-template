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
public class InvalidLaureateCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public InvalidLaureateCriterion(SerializerFactory serializerFactory) {
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
        Laureate laureate = context.entity();

        if (laureate == null) {
            return EvaluationOutcome.fail("Laureate entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Required fields validation (based on Laureate.isValid)
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

        // computedAge quality check
        if (laureate.getComputedAge() != null && laureate.getComputedAge() < 0) {
            return EvaluationOutcome.fail("computedAge cannot be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // born date format validation (should be ISO date YYYY-MM-DD)
        String born = laureate.getBorn();
        if (born != null && !born.isBlank()) {
            try {
                LocalDate.parse(born);
            } catch (DateTimeParseException e) {
                return EvaluationOutcome.fail("born is not a valid ISO date (YYYY-MM-DD)", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } else {
            // born is optional in entity definition, but warn if missing since it's useful for enrichment
            // We treat missing born as validation failure only if computedAge is present (inconsistent)
            if (laureate.getComputedAge() != null) {
                return EvaluationOutcome.fail("computedAge provided but born is missing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // died date format validation and logical check (if present)
        String died = laureate.getDied();
        if (died != null && !died.isBlank()) {
            try {
                LocalDate diedDate = LocalDate.parse(died);
                if (born != null && !born.isBlank()) {
                    try {
                        LocalDate bornDate = LocalDate.parse(born);
                        if (diedDate.isBefore(bornDate)) {
                            return EvaluationOutcome.fail("died date is before born date", StandardEvalReasonCategories.VALIDATION_FAILURE);
                        }
                    } catch (DateTimeParseException ignore) {
                        // born parse error already handled above
                    }
                }
            } catch (DateTimeParseException e) {
                return EvaluationOutcome.fail("died is not a valid ISO date (YYYY-MM-DD)", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // year should be a 4-digit numeric year
        String year = laureate.getYear();
        if (year != null && !year.isBlank()) {
            if (!year.matches("\\d{4}")) {
                return EvaluationOutcome.fail("year must be a 4-digit numeric year", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            // If born is present, sanity-check that year is not earlier than born year
            if (born != null && !born.isBlank()) {
                try {
                    int awardYear = Integer.parseInt(year);
                    int bornYear = LocalDate.parse(born).getYear();
                    if (awardYear < bornYear) {
                        return EvaluationOutcome.fail("award year is earlier than born year", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                    }
                } catch (Exception ignore) {
                    // parsing issues covered by previous checks
                }
            }
        }

        return EvaluationOutcome.success();
    }
}
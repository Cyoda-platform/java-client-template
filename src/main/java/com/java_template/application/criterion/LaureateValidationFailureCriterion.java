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

@Component
public class LaureateValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public LaureateValidationFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
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
        if (laureate.getLaureateId() == null || laureate.getLaureateId() == 0) {
            return EvaluationOutcome.fail("Laureate ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (laureate.getFirstname() == null || laureate.getFirstname().isEmpty()) {
            return EvaluationOutcome.fail("First name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (laureate.getSurname() == null || laureate.getSurname().isEmpty()) {
            return EvaluationOutcome.fail("Surname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (laureate.getYear() == null || laureate.getYear().isEmpty()) {
            return EvaluationOutcome.fail("Year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (laureate.getCategory() == null || laureate.getCategory().isEmpty()) {
            return EvaluationOutcome.fail("Category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Validate date format for born and died if present
        if (laureate.getBorn() != null && !laureate.getBorn().isEmpty()) {
            if (!isValidIsoDate(laureate.getBorn())) {
                return EvaluationOutcome.fail("Born date is not a valid ISO 8601 date", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }
        if (laureate.getDied() != null && !laureate.getDied().isEmpty()) {
            if (!isValidIsoDate(laureate.getDied())) {
                return EvaluationOutcome.fail("Died date is not a valid ISO 8601 date", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }
        return EvaluationOutcome.success();
    }

    private boolean isValidIsoDate(String dateStr) {
        try {
            java.time.OffsetDateTime.parse(dateStr);
            return true;
        } catch (Exception e) {
            try {
                java.time.LocalDate.parse(dateStr);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}
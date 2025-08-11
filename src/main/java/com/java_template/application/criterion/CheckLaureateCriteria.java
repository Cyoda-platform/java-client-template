package com.java_template.application.criterion;

import com.java_template.application.entity.Laureate.version_1.Laureate;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class CheckLaureateCriteria implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CheckLaureateCriteria(SerializerFactory serializerFactory) {
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
        if (laureate.getLaureateId() == null) {
            return EvaluationOutcome.fail("LaureateId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (laureate.getFirstname() == null || laureate.getFirstname().trim().isEmpty()) {
            return EvaluationOutcome.fail("Firstname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (laureate.getSurname() == null || laureate.getSurname().trim().isEmpty()) {
            return EvaluationOutcome.fail("Surname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (laureate.getYear() == null || laureate.getYear().trim().isEmpty()) {
            return EvaluationOutcome.fail("Year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (laureate.getCategory() == null || laureate.getCategory().trim().isEmpty()) {
            return EvaluationOutcome.fail("Category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Validate date formats
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        if (laureate.getBorn() != null && !laureate.getBorn().trim().isEmpty()) {
            try {
                LocalDate.parse(laureate.getBorn(), formatter);
            } catch (DateTimeParseException e) {
                return EvaluationOutcome.fail("Born date is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }
        if (laureate.getDied() != null && !laureate.getDied().trim().isEmpty()) {
            try {
                LocalDate.parse(laureate.getDied(), formatter);
            } catch (DateTimeParseException e) {
                return EvaluationOutcome.fail("Died date is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }
        return EvaluationOutcome.success();
    }
}

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
public class LaureateDataValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public LaureateDataValidCriterion(SerializerFactory serializerFactory) {
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
        if (laureate.getLaureateId() == null || laureate.getLaureateId().isEmpty()) {
            return EvaluationOutcome.fail("Laureate ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (laureate.getFirstname() == null || laureate.getFirstname().isEmpty()) {
            return EvaluationOutcome.fail("Firstname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (laureate.getSurname() == null || laureate.getSurname().isEmpty()) {
            return EvaluationOutcome.fail("Surname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (laureate.getCategory() == null || laureate.getCategory().isEmpty()) {
            return EvaluationOutcome.fail("Category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Validate date formats (basic ISO check)
        if (laureate.getBorn() != null && !laureate.getBorn().matches("\\d{4}-\\d{2}-\\d{2}")) {
            return EvaluationOutcome.fail("Born date format invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (laureate.getDied() != null && !laureate.getDied().isEmpty() && !laureate.getDied().matches("\\d{4}-\\d{2}-\\d{2}")) {
            return EvaluationOutcome.fail("Died date format invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Validate country code length
        if (laureate.getBorncountrycode() != null && laureate.getBorncountrycode().length() != 2) {
            return EvaluationOutcome.fail("Born country code must be 2 characters", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Additional business rules can be added here
        return EvaluationOutcome.success();
    }
}

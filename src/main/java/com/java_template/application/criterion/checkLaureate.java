package com.java_template.application.criterion;

import com.java_template.application.entity.Laureate;
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
public class checkLaureate implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public checkLaureate(SerializerFactory serializerFactory) {
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
             return EvaluationOutcome.fail("Laureate ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getFirstname() == null || laureate.getFirstname().trim().isEmpty()) {
             return EvaluationOutcome.fail("Firstname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getSurname() == null || laureate.getSurname().trim().isEmpty()) {
             return EvaluationOutcome.fail("Surname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getBorn() == null) {
             return EvaluationOutcome.fail("Birth date is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getBorncountry() == null || laureate.getBorncountry().trim().isEmpty()) {
             return EvaluationOutcome.fail("Birth country is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getBorncountrycode() == null || laureate.getBorncountrycode().trim().isEmpty()) {
             return EvaluationOutcome.fail("Birth country code is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getYear() == null || laureate.getYear().trim().isEmpty()) {
             return EvaluationOutcome.fail("Award year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getCategory() == null || laureate.getCategory().trim().isEmpty()) {
             return EvaluationOutcome.fail("Category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (laureate.getMotivation() == null || laureate.getMotivation().trim().isEmpty()) {
             return EvaluationOutcome.fail("Motivation is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Gender can be optional but if present, check allowed values
         if (laureate.getGender() != null) {
             String gender = laureate.getGender().toLowerCase();
             if (!gender.equals("male") && !gender.equals("female") && !gender.equals("other")) {
                 return EvaluationOutcome.fail("Invalid gender value", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }
         return EvaluationOutcome.success();
    }
}

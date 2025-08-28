package com.java_template.application.criterion;

import com.java_template.application.entity.pet.version_1.Pet;
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
public class AgeEligibilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AgeEligibilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Pet.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet pet = context.entity();

         // Requirement: Pet age must be present and reasonable.
         // Business rule: Pet must be at least 1 year old to be eligible for adoption processing via this criterion.
         Integer age = pet.getAge();

         if (age == null) {
             return EvaluationOutcome.fail("Pet age is required for eligibility check", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (age < 0) {
             return EvaluationOutcome.fail("Pet age is invalid (negative)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         final int MINIMUM_ADOPTABLE_AGE_YEARS = 1;
         if (age < MINIMUM_ADOPTABLE_AGE_YEARS) {
             return EvaluationOutcome.fail(
                 String.format("Pet is too young for adoption (must be at least %d year(s))", MINIMUM_ADOPTABLE_AGE_YEARS),
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
             );
         }

        return EvaluationOutcome.success();
    }
}
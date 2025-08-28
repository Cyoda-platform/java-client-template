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
public class PetDataCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetDataCompleteCriterion(SerializerFactory serializerFactory) {
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
         if (pet == null) {
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         StringBuilder missing = new StringBuilder();

         if (pet.getId() == null || pet.getId().isBlank()) {
             appendField(missing, "id");
         }
         if (pet.getName() == null || pet.getName().isBlank()) {
             appendField(missing, "name");
         }
         if (pet.getSpecies() == null || pet.getSpecies().isBlank()) {
             appendField(missing, "species");
         }
         if (pet.getSex() == null || pet.getSex().isBlank()) {
             appendField(missing, "sex");
         }
         if (pet.getStatus() == null || pet.getStatus().isBlank()) {
             appendField(missing, "status");
         }

         if (missing.length() > 0) {
             String msg = "Missing required fields: " + missing.toString();
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (pet.getAge() != null && pet.getAge() < 0) {
             return EvaluationOutcome.fail("Age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All required checks passed for data completeness
         return EvaluationOutcome.success();
    }

    private void appendField(StringBuilder sb, String field) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(field);
    }
}
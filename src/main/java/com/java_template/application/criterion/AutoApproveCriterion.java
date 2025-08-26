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

import java.util.List;

@Component
public class AutoApproveCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AutoApproveCriterion(SerializerFactory serializerFactory) {
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
             return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic entity validity
         if (!pet.isValid()) {
             return EvaluationOutcome.fail("Basic entity validation failed", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: only auto-approve pets that are currently available
         String availability = pet.getAvailability_status();
         if (availability == null || !"available".equalsIgnoreCase(availability)) {
             return EvaluationOutcome.fail("Pet not available for auto-approval", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality: require vaccination for auto-approval
         String health = pet.getHealth_status();
         if (health == null || !"vaccinated".equalsIgnoreCase(health)) {
             return EvaluationOutcome.fail("Pet must be vaccinated for auto-approval", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Data quality: require at least one photo
         List<String> photos = pet.getPhotos();
         if (photos == null || photos.isEmpty()) {
             return EvaluationOutcome.fail("At least one photo is required for auto-approval", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: temperament must not contain high-risk tags
         List<String> temperament = pet.getTemperament_tags();
         if (temperament != null) {
             for (String t : temperament) {
                 if (t == null) continue;
                 String normalized = t.trim().toLowerCase();
                 if ("aggressive".equals(normalized) || "dangerous".equals(normalized) || "attack".equals(normalized)) {
                     return EvaluationOutcome.fail("Pet temperament flagged as high-risk", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             }
         }

         // Data quality: age sanity checks to avoid obviously incorrect values
         Integer ageVal = pet.getAge_value();
         String ageUnit = pet.getAge_unit();
         if (ageVal != null && ageUnit != null) {
             if ("years".equalsIgnoreCase(ageUnit) && ageVal > 30) {
                 return EvaluationOutcome.fail("Unrealistic age value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if ("months".equalsIgnoreCase(ageUnit) && ageVal > 360) {
                 return EvaluationOutcome.fail("Unrealistic age value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

        return EvaluationOutcome.success();
    }
}
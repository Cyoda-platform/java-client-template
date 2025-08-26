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
public class ValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationCriterion(SerializerFactory serializerFactory) {
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

         // Required fields (based on Pet.isValid rules)
         if (pet.getId() == null || pet.getId().isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (pet.getName() == null || pet.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (pet.getSpecies() == null || pet.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (pet.getStatus() == null || pet.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (pet.getCreatedAt() == null || pet.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (pet.getUpdatedAt() == null || pet.getUpdatedAt().isBlank()) {
             return EvaluationOutcome.fail("updatedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Optional string fields must not be blank if present
         if (pet.getSourceId() != null && pet.getSourceId().isBlank()) {
             return EvaluationOutcome.fail("sourceId, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (pet.getSourceUpdatedAt() != null && pet.getSourceUpdatedAt().isBlank()) {
             return EvaluationOutcome.fail("sourceUpdatedAt, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (pet.getDescription() != null && pet.getDescription().isBlank()) {
             return EvaluationOutcome.fail("description, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (pet.getBreed() != null && pet.getBreed().isBlank()) {
             return EvaluationOutcome.fail("breed, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Age must be non-negative if present
         if (pet.getAge() != null && pet.getAge() < 0) {
             return EvaluationOutcome.fail("age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Collections must not contain null/blank entries
         if (pet.getImages() != null) {
             for (String img : pet.getImages()) {
                 if (img == null || img.isBlank()) {
                     return EvaluationOutcome.fail("images must not contain null or blank entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }
         if (pet.getTags() != null) {
             for (String tag : pet.getTags()) {
                 if (tag == null || tag.isBlank()) {
                     return EvaluationOutcome.fail("tags must not contain null or blank entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Business rule example: species should be a known type (simple heuristic)
         String species = pet.getSpecies();
         if (species != null) {
             String normalized = species.trim().toLowerCase();
             if (!(normalized.equals("cat") || normalized.equals("dog") || normalized.equals("rabbit") || normalized.equals("other"))) {
                 // Not strictly a validation failure; mark as data quality for review
                 return EvaluationOutcome.fail("species value is unusual: " + species, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }
}
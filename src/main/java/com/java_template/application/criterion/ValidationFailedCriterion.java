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
public class ValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailedCriterion(SerializerFactory serializerFactory) {
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
         Pet entity = context.entity();

         if (entity == null) {
             logger.debug("ValidationFailedCriterion: entity is null");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identifiers and core fields
         if (entity.getPetId() == null || entity.getPetId().isBlank()) {
             return EvaluationOutcome.fail("petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Photo URLs: required and must not contain blank entries
         if (entity.getPhotoUrls() == null || entity.getPhotoUrls().isEmpty()) {
             return EvaluationOutcome.fail("at least one photoUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         for (String url : entity.getPhotoUrls()) {
             if (url == null || url.isBlank()) {
                 return EvaluationOutcome.fail("photoUrls contains blank or null url", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Tags must be a non-null collection and not contain blank entries (domain requires tags list present)
         if (entity.getTags() == null) {
             return EvaluationOutcome.fail("tags collection must be present (may be empty)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         for (String t : entity.getTags()) {
             if (t == null || t.isBlank()) {
                 return EvaluationOutcome.fail("tags contains blank or null entry", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Age, if present, must be non-negative
         if (entity.getAge() != null && entity.getAge() < 0) {
             return EvaluationOutcome.fail("age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}